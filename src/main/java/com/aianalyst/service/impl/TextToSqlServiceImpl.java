package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.DeepSeekChatService;
import com.aianalyst.service.QueryIntentSafetyValidator;
import com.aianalyst.service.QuerySemanticValidator;
import com.aianalyst.service.RateLimitService;
import com.aianalyst.service.SqlAuditService;
import com.aianalyst.service.TextToSqlPromptBuilder;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.SqlGenerationVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Text-to-SQL 服务只负责模型调用、输出清理和 SQL 审核，不负责数据库执行。
 * 生成与执行职责分离后，模型输出不会跳过安全层直接接触数据库。
 */
@Service
public class TextToSqlServiceImpl implements TextToSqlService {

    private static final int MAX_CORRECTION_ERROR_LENGTH = 1_000;

    private final QueryIntentSafetyValidator queryIntentSafetyValidator;
    private final QuerySemanticValidator querySemanticValidator;
    private final RateLimitService rateLimitService;
    private final TextToSqlPromptBuilder promptBuilder;
    private final DeepSeekChatService deepSeekChatService;
    private final SqlAuditService sqlAuditService;

    public TextToSqlServiceImpl(QueryIntentSafetyValidator queryIntentSafetyValidator,
                                QuerySemanticValidator querySemanticValidator,
                                RateLimitService rateLimitService,
                                TextToSqlPromptBuilder promptBuilder,
                                DeepSeekChatService deepSeekChatService,
                                SqlAuditService sqlAuditService) {
        this.queryIntentSafetyValidator = queryIntentSafetyValidator;
        this.querySemanticValidator = querySemanticValidator;
        this.rateLimitService = rateLimitService;
        this.promptBuilder = promptBuilder;
        this.deepSeekChatService = deepSeekChatService;
        this.sqlAuditService = sqlAuditService;
    }

    @Override
    public SqlGenerationVO generateSql(Long userId, String question) {
        // 先拒绝写操作意图，防止模型将“删除”擅自改写成 SELECT 后返回敏感数据。
        queryIntentSafetyValidator.validateReadOnlyIntent(question);
        // 先校验可确定的业务参数，非法 Top N 不应消耗 Redis 令牌、模型额度或数据库资源。
        querySemanticValidator.validate(question);
        // 先限流再调用模型，避免高频请求消耗外部 API 额度并放大数据库压力。
        if (!rateLimitService.tryAcquire(userId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }

        String auditedSql = generateAndAudit(promptBuilder.build(question));
        return new SqlGenerationVO(question, auditedSql);
    }

    @Override
    public String correctSql(String question, String failedSql, String databaseError) {
        // 自纠错属于同一次用户查询，不额外消耗一次“查询次数”；但错误文本长度必须受控。
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
