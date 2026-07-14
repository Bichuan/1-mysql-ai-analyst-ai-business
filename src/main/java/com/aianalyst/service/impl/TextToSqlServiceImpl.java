package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.DeepSeekChatService;
import com.aianalyst.service.SqlAuditService;
import com.aianalyst.service.TextToSqlPromptBuilder;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.SqlGenerationVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Text-to-SQL 服务只负责模型调用、输出清理和 SQL 审核，不负责请求频控。
 * 请求意图校验和限流统一由 QueryRequestGuard 在缓存之前完成。
 */
@Service
public class TextToSqlServiceImpl implements TextToSqlService {

    private static final int MAX_CORRECTION_ERROR_LENGTH = 1_000;

    private final TextToSqlPromptBuilder promptBuilder;
    private final DeepSeekChatService deepSeekChatService;
    private final SqlAuditService sqlAuditService;

    public TextToSqlServiceImpl(TextToSqlPromptBuilder promptBuilder,
                                DeepSeekChatService deepSeekChatService,
                                SqlAuditService sqlAuditService) {
        this.promptBuilder = promptBuilder;
        this.deepSeekChatService = deepSeekChatService;
        this.sqlAuditService = sqlAuditService;
    }

    @Override
    public SqlGenerationVO generateSql(String question) {
        String auditedSql = generateAndAudit(promptBuilder.build(question));
        return new SqlGenerationVO(question, auditedSql);
    }

    @Override
    public String correctSql(String question, String failedSql, String databaseError) {
        // 自纠错属于同一次已通过限流的用户查询，不额外消耗一次令牌；但错误文本长度必须受控。
        String safeError = truncateErrorMessage(databaseError);
        return generateAndAudit(promptBuilder.buildCorrection(question, failedSql, safeError));
    }

    private String generateAndAudit(String prompt) {
        String sql = stripMarkdownFence(deepSeekChatService.generate(prompt));
        if (!StringUtils.hasText(sql)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "模型未返回可用 SQL");
        }
        // 去掉 Markdown 只是格式兼容；真正的安全判断必须依赖 JSqlParser 审核。
        return sqlAuditService.auditAndNormalize(sql);
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
        String value = databaseError.trim();
        // 防止异常堆栈或超长错误信息膨胀 Prompt、增加模型费用。
        return value.length() <= MAX_CORRECTION_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_CORRECTION_ERROR_LENGTH) + "...";
    }
}
