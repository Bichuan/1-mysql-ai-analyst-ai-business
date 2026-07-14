package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.service.DataMaskingService;
import com.aianalyst.service.DataQueryService;
import com.aianalyst.service.QueryHistoryService;
import com.aianalyst.service.ResultAnalysisService;
import com.aianalyst.service.SqlExecutionService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.QueryResultVO;
import com.aianalyst.vo.SqlGenerationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private final ResultAnalysisService resultAnalysisService;
    private final QueryHistoryService queryHistoryService;

    public DataQueryServiceImpl(TextToSqlService textToSqlService,
                                SqlExecutionService sqlExecutionService,
                                DataMaskingService dataMaskingService,
                                ResultAnalysisService resultAnalysisService,
                                QueryHistoryService queryHistoryService) {
        this.textToSqlService = textToSqlService;
        this.sqlExecutionService = sqlExecutionService;
        this.dataMaskingService = dataMaskingService;
        this.resultAnalysisService = resultAnalysisService;
        this.queryHistoryService = queryHistoryService;
    }

    @Override
    public QueryResultVO query(Long userId, String question) {
        String sql = null;
        int totalSqlExecutionTime = 0;
        try {
            // generateSql 内部已经进行限流和首轮安全审核；后续只处理已经审核的 SQL。
            SqlGenerationVO generatedSql = textToSqlService.generateSql(userId, question);
            sql = generatedSql.sql();

            for (int correctionAttempt = 0; ; correctionAttempt++) {
                long executionStartedAt = System.nanoTime();
                try {
                    List<Map<String, Object>> rawRows = sqlExecutionService.executeAuditedSelect(sql);
                    totalSqlExecutionTime = addExecutionTime(totalSqlExecutionTime, executionStartedAt);
                    // 原始结果仅在当前方法内短暂存在；对外响应、AI 总结和审计历史都只能使用脱敏副本。
                    List<Map<String, Object>> maskedRows = dataMaskingService.maskRows(rawRows);
                    // 总结失败时 ResultAnalysisService 会返回降级文案，不能让可用查询结果被外部模型故障拖垮。
                    String summary = resultAnalysisService.analyze(
                            generatedSql.question(), sql, maskedRows, maskedRows.size());
                    QueryResultVO result = new QueryResultVO(
                            generatedSql.question(), sql, maskedRows, maskedRows.size(), summary);
                    recordSuccess(userId, generatedSql.question(), sql, maskedRows, summary, totalSqlExecutionTime);
                    return result;
                } catch (SqlExecutionException exception) {
                    totalSqlExecutionTime = addExecutionTime(totalSqlExecutionTime, executionStartedAt);
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
        } catch (RuntimeException exception) {
            recordFailure(userId, question, sql, totalSqlExecutionTime, exception);
            throw exception;
        }
    }

    private void recordSuccess(Long userId,
                               String question,
                               String sql,
                               List<Map<String, Object>> maskedRows,
                               String summary,
                               int executionTime) {
        queryHistoryService.recordAsync(new QueryHistoryRecordCommand(
                userId, question, sql, "PASS", null, maskedRows, summary,
                executionTime, "SUCCESS", null));
    }

    private void recordFailure(Long userId,
                               String question,
                               String sql,
                               int executionTime,
                               RuntimeException exception) {
        boolean auditRejected = isAuditRejection(exception);
        queryHistoryService.recordAsync(new QueryHistoryRecordCommand(
                userId,
                question,
                sql,
                auditRejected ? "REJECT" : sql == null ? null : "PASS",
                auditRejected ? exception.getMessage() : null,
                null,
                null,
                executionTime == 0 ? null : executionTime,
                auditRejected ? "AUDIT_REJECT" : "FAIL",
                publicErrorMessage(exception)));
    }

    private boolean isAuditRejection(RuntimeException exception) {
        if (!(exception instanceof BusinessException businessException)) {
            return false;
        }
        return businessException.getResultCode() == ResultCode.SQL_AUDIT_FAILED
                || businessException.getResultCode() == ResultCode.READ_ONLY_QUERY_REQUIRED;
    }

    private String publicErrorMessage(RuntimeException exception) {
        if (exception instanceof SqlExecutionException) {
            return ResultCode.SQL_EXECUTION_FAILED.getMessage();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        return ResultCode.SYSTEM_ERROR.getMessage();
    }

    private int addExecutionTime(int accumulatedMilliseconds, long startedAtNanos) {
        long elapsedMilliseconds = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        // 字段为 INT；极端情况下做上限保护，避免累加溢出导致出现负耗时。
        return (int) Math.min(Integer.MAX_VALUE, (long) accumulatedMilliseconds + elapsedMilliseconds);
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
