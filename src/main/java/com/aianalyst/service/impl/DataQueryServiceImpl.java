package com.aianalyst.service.impl;

import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.service.DataMaskingService;
import com.aianalyst.service.DataQueryService;
import com.aianalyst.service.SqlExecutionService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.QueryResultVO;
import com.aianalyst.vo.SqlGenerationVO;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.BadSqlGrammarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 完整查询编排层：自然语言 -> 生成 SQL -> 安全审核 -> 只读执行。
 * Controller 只转发请求，跨多个服务的流程控制集中在这里，便于测试和后续扩展。
 */
@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryServiceImpl.class);
    private static final int MAX_SQL_CORRECTION_ATTEMPTS = 2;

    private final TextToSqlService textToSqlService;
    private final SqlExecutionService sqlExecutionService;
    private final DataMaskingService dataMaskingService;

    public DataQueryServiceImpl(TextToSqlService textToSqlService,
                                SqlExecutionService sqlExecutionService,
                                DataMaskingService dataMaskingService) {
        this.textToSqlService = textToSqlService;
        this.sqlExecutionService = sqlExecutionService;
        this.dataMaskingService = dataMaskingService;
    }

    @Override
    public QueryResultVO query(Long userId, String question) {
        // generateSql 内部已进行限流和首轮安全审核；后续只处理已经审核的 SQL。
        SqlGenerationVO generatedSql = textToSqlService.generateSql(userId, question);
        String sql = generatedSql.sql();

        for (int correctionAttempt = 0; ; correctionAttempt++) {
            try {
                List<Map<String, Object>> rawRows = sqlExecutionService.executeAuditedSelect(sql);
                // 原始结果仅在当前方法内短暂存在；对外响应和后续 AI 总结都只能使用脱敏副本。
                List<Map<String, Object>> maskedRows = dataMaskingService.maskRows(rawRows);
                return new QueryResultVO(generatedSql.question(), sql, maskedRows, maskedRows.size());
            } catch (SqlExecutionException exception) {
                // 只有 SQL 语法/字段类错误可能由模型修复；网络、超时和权限错误重试没有意义。
                if (!isCorrectableSyntaxFailure(exception)
                        || correctionAttempt >= MAX_SQL_CORRECTION_ATTEMPTS) {
                    throw exception;
                }

                int currentAttempt = correctionAttempt + 1;
                // 上限为 2：避免错误 Prompt 或模型异常造成无限循环和不可控模型费用。
                log.warn("Generated SQL had a correctable grammar failure; requesting correction attempt {}/{}",
                        currentAttempt, MAX_SQL_CORRECTION_ATTEMPTS);
                // correctSql 会重新走 Day8 的安全审核，修复流程不能绕过审核直接执行。
                sql = textToSqlService.correctSql(question, sql, correctionErrorMessage(exception));
            }
        }
    }

    private boolean isCorrectableSyntaxFailure(SqlExecutionException exception) {
        // Spring 会把字段不存在、语法不合法等 MySQL 错误转换为 BadSqlGrammarException。
        return exception.getCause() instanceof BadSqlGrammarException;
    }

    private String correctionErrorMessage(SqlExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null
                ? "未知 SQL 语法错误"
                : cause.getMessage();
    }
}
