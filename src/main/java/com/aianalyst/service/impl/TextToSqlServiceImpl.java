package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.dto.SqlGenerationOutcome;
import com.aianalyst.service.ModelCallAwaiter;
import com.aianalyst.service.ModelCallType;
import com.aianalyst.service.ModelResilienceGateway;
import com.aianalyst.service.SqlAuditService;
import com.aianalyst.service.prompt.TextToSqlPromptBuilder;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.SqlGenerationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Text-to-SQL 服务只负责模型调用、输出清理和 SQL 审核，不负责请求频控。
 * 请求意图校验和限流统一由 QueryRequestGuard 在缓存之前完成。
 */
@Service
public class TextToSqlServiceImpl implements TextToSqlService {

    private static final Logger log = LoggerFactory.getLogger(TextToSqlServiceImpl.class);
    private static final int MAX_CORRECTION_ERROR_LENGTH = 1_000;
    private static final int MAX_FAILED_CANDIDATE_LENGTH = 8_000;

    private final TextToSqlPromptBuilder promptBuilder;
    private final ModelResilienceGateway modelResilienceGateway;
    private final SqlAuditService sqlAuditService;

    public TextToSqlServiceImpl(TextToSqlPromptBuilder promptBuilder,
                                ModelResilienceGateway modelResilienceGateway,
                                SqlAuditService sqlAuditService) {
        this.promptBuilder = promptBuilder;
        this.modelResilienceGateway = modelResilienceGateway;
        this.sqlAuditService = sqlAuditService;
    }

    @Override
    public SqlGenerationVO generateSql(String question) {
        return generateSqlWithAuditRecovery(question).result();
    }

    @Override
    public SqlGenerationOutcome generateSqlWithAuditRecovery(String question) {
        String candidate = generateCandidate(promptBuilder.build(question));
        try {
            String auditedSql = sqlAuditService.auditAndNormalize(candidate);
            return SqlGenerationOutcome.initial(new SqlGenerationVO(question, auditedSql));
        } catch (BusinessException exception) {
            if (!isCorrectableAuditFormatFailure(exception)) {
                throw exception;
            }
            log.warn("Model SQL output failed format audit; requesting one bounded correction. reason={}",
                    exception.getMessage());
            String correctionPrompt = promptBuilder.buildAuditCorrection(
                    question,
                    truncate(candidate, MAX_FAILED_CANDIDATE_LENGTH),
                    truncate(exception.getMessage(), MAX_CORRECTION_ERROR_LENGTH));
            String correctedCandidate = generateCandidate(correctionPrompt);
            String auditedSql = sqlAuditService.auditAndNormalize(correctedCandidate);
            return new SqlGenerationOutcome(new SqlGenerationVO(question, auditedSql), 1);
        }
    }

    @Override
    public String correctSql(String question, String failedSql, String databaseError) {
        // 自纠错属于同一次已通过限流的用户查询，不额外消耗一次令牌；但错误文本长度必须受控。
        String safeError = truncateErrorMessage(databaseError);
        return generateAndAudit(promptBuilder.buildCorrection(question, failedSql, safeError));
    }

    private String generateAndAudit(String prompt) {
        return sqlAuditService.auditAndNormalize(generateCandidate(prompt));
    }

    private String generateCandidate(String prompt) {
        String candidate = stripMarkdownFence(ModelCallAwaiter.await(
                ModelCallType.TEXT_TO_SQL,
                () -> modelResilienceGateway.generateSql(prompt)));
        if (!StringUtils.hasText(candidate)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "模型未返回可用 SQL");
        }
        // 去掉 Markdown 只是格式兼容；真正的安全判断必须依赖 JSqlParser 审核。
        return candidate;
    }

    private boolean isCorrectableAuditFormatFailure(BusinessException exception) {
        if (exception.getResultCode() != ResultCode.SQL_AUDIT_FAILED) {
            return false;
        }
        return "只允许单条 SQL 语句".equals(exception.getMessage())
                || "SQL 语法解析失败".equals(exception.getMessage());
    }

    private String stripMarkdownFence(String response) {
        String value = response == null ? "" : response.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            value = firstLineEnd >= 0 ? value.substring(firstLineEnd + 1) : "";
        }
        if (value.endsWith("```")) {
            value = value.substring(0, value.length() - 3);
        }
        return value.trim();
    }

    private String truncateErrorMessage(String databaseError) {
        if (!StringUtils.hasText(databaseError)) {
            return "未知 SQL 执行错误";
        }
        return truncate(databaseError, MAX_CORRECTION_ERROR_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        // 防止异常堆栈或模型候选膨胀 Prompt、增加费用。
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }
}
