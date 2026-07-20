package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.dto.ResolvedConversationQuestion;
import com.aianalyst.dto.SqlGenerationOutcome;
import com.aianalyst.service.ConversationContextService;
import com.aianalyst.service.ConversationQuestionResolver;
import com.aianalyst.service.DataMaskingService;
import com.aianalyst.service.DataQueryService;
import com.aianalyst.service.QueryCacheService;
import com.aianalyst.service.QueryHistoryService;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.QueryRequestGuard;
import com.aianalyst.service.ResultAnalysisService;
import com.aianalyst.service.SqlExecutionService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.QueryResultVO;
import com.aianalyst.vo.SqlGenerationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/**
 * 完整查询编排层：请求守卫 -> Redis 缓存 -> 生成 SQL -> 安全审核 -> 只读执行。
 * Controller 只转发请求，跨多个服务的流程控制集中在这里，便于测试和后续扩展。
 */
@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final Logger log = LoggerFactory.getLogger(DataQueryServiceImpl.class);
    private static final int MAX_SQL_CORRECTION_ATTEMPTS = 2;

    /**
     * MySQL 可能用 SQLState 42000 表示权限违规，Spring 又可能将其翻译为 BadSqlGrammarException；
     * 因此必须优先识别厂商错误码，保证权限错误永远不会进入 AI 自纠错。
     */
    private static final Set<Integer> MYSQL_PERMISSION_DENIED_ERROR_CODES = Set.of(
            1044, // Access denied for user to database
            1045, // Access denied during authentication
            1142, // Command denied for table
            1143, // Command denied for column
            1227, // Specific privilege is required
            1370  // Execute command denied for stored routine
    );
    private static final String INVALID_AUTHORIZATION_SQL_STATE_PREFIX = "28";

    private final QueryRequestGuard queryRequestGuard;
    private final QueryCacheService queryCacheService;
    private final TextToSqlService textToSqlService;
    private final SqlExecutionService sqlExecutionService;
    private final DataMaskingService dataMaskingService;
    private final ResultAnalysisService resultAnalysisService;
    private final QueryHistoryService queryHistoryService;
    private final QueryMetricsService queryMetricsService;
    private final ConversationContextService conversationContextService;
    private final ConversationQuestionResolver conversationQuestionResolver;

    public DataQueryServiceImpl(QueryRequestGuard queryRequestGuard,
                                QueryCacheService queryCacheService,
                                TextToSqlService textToSqlService,
                                SqlExecutionService sqlExecutionService,
                                DataMaskingService dataMaskingService,
                                ResultAnalysisService resultAnalysisService,
                                QueryHistoryService queryHistoryService,
                                QueryMetricsService queryMetricsService,
                                ConversationContextService conversationContextService,
                                ConversationQuestionResolver conversationQuestionResolver) {
        this.queryRequestGuard = queryRequestGuard;
        this.queryCacheService = queryCacheService;
        this.textToSqlService = textToSqlService;
        this.sqlExecutionService = sqlExecutionService;
        this.dataMaskingService = dataMaskingService;
        this.resultAnalysisService = resultAnalysisService;
        this.queryHistoryService = queryHistoryService;
        this.queryMetricsService = queryMetricsService;
        this.conversationContextService = conversationContextService;
        this.conversationQuestionResolver = conversationQuestionResolver;
    }

    @Override
    public QueryResultVO query(Long userId, String requestedConversationId, String question) {
        long queryStartedAt = System.nanoTime();
        String sql = null;
        String conversationId = null;
        String standaloneQuestion = question;
        int totalSqlExecutionTime = 0;
        try {
            // Guard 先进行意图、语义校验和限流；缓存命中也必须消耗一次额度，不能绕过流量保护。
            queryRequestGuard.validateAndAcquire(userId, question);
            // Only validated raw input may create or resume reusable conversation context.
            conversationId = conversationContextService.openSession(
                    userId, requestedConversationId, question);
            ResolvedConversationQuestion resolvedQuestion = conversationQuestionResolver.resolve(
                    userId, conversationId, question);
            standaloneQuestion = resolvedQuestion.standaloneQuestion();
            // Rewritten content is model output: validate it again, but do not consume a second rate-limit token.
            queryRequestGuard.validate(standaloneQuestion);

            Optional<QueryResultVO> cachedResult = queryCacheService.get(userId, standaloneQuestion);
            // 查询缓存
            if (cachedResult.isPresent()) {
                QueryResultVO result = withCurrentQuestion(cachedResult.get(), question, conversationId);
                // 缓存命中同样是一次用户查询，需要保留审计轨迹；SQL 执行耗时为 0。
                CompletableFuture<Long> historyIdFuture = recordSuccess(
                        userId, standaloneQuestion, result.sql(), result.rows(), result.summary(), 0);
                conversationContextService.recordTurn(
                        userId, conversationId, question, standaloneQuestion, result.summary(),
                        "SUCCESS", historyIdFuture);
                return result;
            }

            SqlGenerationOutcome generationOutcome =
                    textToSqlService.generateSqlWithAuditRecovery(standaloneQuestion);
            SqlGenerationVO generatedSql = generationOutcome.result();
            sql = generatedSql.sql();
            int correctionAttemptsUsed = generationOutcome.correctionAttemptsUsed();

            for (;;) {
                long executionStartedAt = System.nanoTime();
                try {
                    List<Map<String, Object>> rawRows = sqlExecutionService.executeAuditedSelect(sql);
                    totalSqlExecutionTime = addExecutionTime(totalSqlExecutionTime, executionStartedAt);
                    // 原始结果仅在当前方法内短暂存在；对外响应、AI 总结、Redis 和审计历史都只能使用脱敏副本。
                    List<Map<String, Object>> maskedRows = dataMaskingService.maskRows(rawRows);
                    // 总结失败时 ResultAnalysisService 会返回降级文案，不能让可用查询结果被外部模型故障拖垮。
                    String summary = resultAnalysisService.analyze(
                            standaloneQuestion, sql, maskedRows, maskedRows.size());
                    QueryResultVO result = new QueryResultVO(
                            question, sql, maskedRows, maskedRows.size(), summary, false,
                            conversationId);
                    // 缓存只写入已审核、已执行、已脱敏且有总结的完整成功响应。
                    queryCacheService.put(userId, standaloneQuestion, result);
                    CompletableFuture<Long> historyIdFuture = recordSuccess(
                            userId, standaloneQuestion, sql, maskedRows, summary, totalSqlExecutionTime);
                    conversationContextService.recordTurn(
                            userId, conversationId, question, standaloneQuestion, summary,
                            "SUCCESS", historyIdFuture);
                    return result;
                } catch (SqlExecutionException exception) {
                    totalSqlExecutionTime = addExecutionTime(totalSqlExecutionTime, executionStartedAt);
                    // 只有 SQL 语法/字段类错误可能由模型修复；网络、超时和权限错误重试没有意义。
                    if (!isCorrectableSyntaxFailure(exception)
                            || correctionAttemptsUsed >= MAX_SQL_CORRECTION_ATTEMPTS) {
                        throw exception;
                    }

                    int currentAttempt = ++correctionAttemptsUsed;
                    // 审核格式纠错与执行语法纠错共享上限 2，避免模型调用次数失控。
                    log.warn("Generated SQL had a correctable grammar failure; requesting correction attempt {}/{}",
                            currentAttempt, MAX_SQL_CORRECTION_ATTEMPTS);
                    // correctSql 会重新走 Day8 的安全审核，修复流程不能绕过审核直接执行。
                    sql = textToSqlService.correctSql(
                            standaloneQuestion, sql, correctionErrorMessage(exception));
                }
            }
        } catch (RuntimeException exception) {
            CompletableFuture<Long> historyIdFuture = recordFailure(
                    userId, standaloneQuestion, sql, totalSqlExecutionTime, exception);
            if (conversationId != null) {
                conversationContextService.recordTurn(
                        userId, conversationId, question, standaloneQuestion, publicErrorMessage(exception),
                        "FAIL", historyIdFuture);
            }
            throw exception;
        } finally {
            // 无论成功、缓存命中或被安全规则拒绝，都记录端到端耗时，便于发现慢查询和外部模型抖动。
            queryMetricsService.recordQueryDuration(System.nanoTime() - queryStartedAt);
        }
    }

    private QueryResultVO withCurrentQuestion(QueryResultVO cachedResult,
                                              String question,
                                              String conversationId) {
        // 缓存 Key 会归一化空白和大小写，但响应应回显用户本次实际输入，而非第一次写入缓存时的文本。
        return new QueryResultVO(question, cachedResult.sql(), cachedResult.rows(),
                cachedResult.rowCount(), cachedResult.summary(), true, conversationId);
    }

    private CompletableFuture<Long> recordSuccess(Long userId,
                                                  String question,
                                                  String sql,
                                                  List<Map<String, Object>> maskedRows,
                                                  String summary,
                                                  int executionTime) {
        return queryHistoryService.recordAsync(new QueryHistoryRecordCommand(
                userId, question, sql, "PASS", null, maskedRows, summary,
                executionTime, "SUCCESS", null));
    }

    private CompletableFuture<Long> recordFailure(Long userId,
                                                  String question,
                                                  String sql,
                                                  int executionTime,
                                                  RuntimeException exception) {
        boolean auditRejected = isAuditRejection(exception);
        return queryHistoryService.recordAsync(new QueryHistoryRecordCommand(
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
                || businessException.getResultCode() == ResultCode.READ_ONLY_QUERY_REQUIRED
                || businessException.getResultCode() == ResultCode.PROMPT_INJECTION_DETECTED;
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
        // 权限错误优先否决：部分 MySQL 42000 错误也会被 Spring 包装成 BadSqlGrammarException。
        if (containsPermissionDeniedCause(exception)) {
            return false;
        }
        // 字段不存在、可解析但数据库不接受的语法等错误，才允许交给模型修复。
        return containsCause(exception, BadSqlGrammarException.class);
    }

    private boolean containsPermissionDeniedCause(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof PermissionDeniedDataAccessException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if ((sqlState != null && sqlState.startsWith(INVALID_AUTHORIZATION_SQL_STATE_PREFIX))
                        || MYSQL_PERMISSION_DENIED_ERROR_CODES.contains(sqlException.getErrorCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private String correctionErrorMessage(SqlExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null
                ? "未知 SQL 语法错误"
                : cause.getMessage();
    }
}
