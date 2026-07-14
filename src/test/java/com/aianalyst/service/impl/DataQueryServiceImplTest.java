package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.common.SqlExecutionException;
import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.service.DataMaskingService;
import com.aianalyst.service.QueryCacheService;
import com.aianalyst.service.QueryHistoryService;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.QueryRequestGuard;
import com.aianalyst.service.ResultAnalysisService;
import com.aianalyst.service.SqlExecutionService;
import com.aianalyst.service.TextToSqlService;
import com.aianalyst.vo.QueryResultVO;
import com.aianalyst.vo.SqlGenerationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQueryServiceImplTest {

    @Mock
    private QueryRequestGuard queryRequestGuard;

    @Mock
    private QueryCacheService queryCacheService;

    @Mock
    private TextToSqlService textToSqlService;

    @Mock
    private SqlExecutionService sqlExecutionService;

    @Mock
    private DataMaskingService dataMaskingService;

    @Mock
    private ResultAnalysisService resultAnalysisService;

    @Mock
    private QueryHistoryService queryHistoryService;

    @Mock
    private QueryMetricsService queryMetricsService;

    @InjectMocks
    private DataQueryServiceImpl dataQueryService;

    @Test
    void shouldGenerateAuditExecuteAndCacheMaskedQuery() {
        String question = "查询前一个客户";
        String auditedSql = "SELECT id, customer_name FROM biz_customer LIMIT 5";
        List<Map<String, Object>> rows = List.of(Map.of("id", 1L, "customer_name", "客户1"));
        List<Map<String, Object>> maskedRows = List.of(Map.of("id", 1L, "customer_name", "客*1"));
        String summary = "共返回 1 条客户数据。";
        when(queryCacheService.get(7L, question)).thenReturn(Optional.empty());
        when(textToSqlService.generateSql(question)).thenReturn(new SqlGenerationVO(question, auditedSql));
        when(sqlExecutionService.executeAuditedSelect(auditedSql)).thenReturn(rows);
        when(dataMaskingService.maskRows(rows)).thenReturn(maskedRows);
        when(resultAnalysisService.analyze(question, auditedSql, maskedRows, 1)).thenReturn(summary);

        QueryResultVO result = dataQueryService.query(7L, question);

        assertThat(result.question()).isEqualTo(question);
        assertThat(result.sql()).isEqualTo(auditedSql);
        assertThat(result.rows()).containsExactlyElementsOf(maskedRows);
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.summary()).isEqualTo(summary);
        assertThat(result.cacheHit()).isFalse();
        verify(queryRequestGuard).validateAndAcquire(7L, question);
        verify(textToSqlService).generateSql(question);
        verify(sqlExecutionService).executeAuditedSelect(auditedSql);
        verify(dataMaskingService).maskRows(rows);
        verify(resultAnalysisService).analyze(question, auditedSql, maskedRows, 1);
        ArgumentCaptor<QueryResultVO> cacheCaptor = ArgumentCaptor.forClass(QueryResultVO.class);
        verify(queryCacheService).put(eq(7L), eq(question), cacheCaptor.capture());
        // 缓存写入参数和接口响应一致，证明 DataQuery 层没有把 rawRows 交给 Redis。
        assertThat(cacheCaptor.getValue().rows()).containsExactlyElementsOf(maskedRows);
        ArgumentCaptor<QueryHistoryRecordCommand> historyCaptor =
                ArgumentCaptor.forClass(QueryHistoryRecordCommand.class);
        verify(queryHistoryService).recordAsync(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
                .extracting(QueryHistoryRecordCommand::userId,
                        QueryHistoryRecordCommand::generatedSql,
                        QueryHistoryRecordCommand::sqlAuditResult,
                        QueryHistoryRecordCommand::maskedRows,
                        QueryHistoryRecordCommand::status)
                .containsExactly(7L, auditedSql, "PASS", maskedRows, "SUCCESS");
        verify(queryMetricsService).recordQueryDuration(anyLong());
    }

    @Test
    void shouldReturnCachedResultWithoutCallingModelOrDatabase() {
        String question = " 查询 客户 ";
        List<Map<String, Object>> maskedRows = List.of(Map.of("email", "t***@gmail.com"));
        QueryResultVO cachedResult = new QueryResultVO("查询 客户", "SELECT email FROM biz_customer",
                maskedRows, 1, "本次查询返回 1 条数据", false);
        when(queryCacheService.get(7L, question)).thenReturn(Optional.of(cachedResult));

        QueryResultVO result = dataQueryService.query(7L, question);

        assertThat(result.question()).isEqualTo(question);
        assertThat(result.rows()).containsExactlyElementsOf(maskedRows);
        assertThat(result.cacheHit()).isTrue();
        verify(queryRequestGuard).validateAndAcquire(7L, question);
        verifyNoInteractions(textToSqlService, sqlExecutionService, dataMaskingService, resultAnalysisService);
        verify(queryHistoryService).recordAsync(new QueryHistoryRecordCommand(
                7L, question, cachedResult.sql(), "PASS", null, maskedRows, cachedResult.summary(),
                0, "SUCCESS", null));
        verify(queryMetricsService).recordQueryDuration(anyLong());
    }

    @Test
    void shouldCorrectBadSqlGrammarAndExecuteCorrectedSql() {
        String question = "查询前一个客户";
        String failedSql = "SELECT customer_nam FROM biz_customer LIMIT 5";
        String correctedSql = "SELECT customer_name FROM biz_customer LIMIT 5";
        List<Map<String, Object>> rows = List.of(Map.of("customer_name", "客户1"));
        List<Map<String, Object>> maskedRows = List.of(Map.of("customer_name", "客*1"));
        String summary = "共返回 1 条客户数据。";
        SqlExecutionException syntaxFailure = new SqlExecutionException(failedSql,
                new BadSqlGrammarException("query", failedSql, new SQLException("Unknown column 'customer_nam'")));
        when(queryCacheService.get(7L, question)).thenReturn(Optional.empty());
        when(textToSqlService.generateSql(question)).thenReturn(new SqlGenerationVO(question, failedSql));
        when(sqlExecutionService.executeAuditedSelect(failedSql)).thenThrow(syntaxFailure);
        when(textToSqlService.correctSql(anyString(), anyString(), anyString())).thenReturn(correctedSql);
        when(sqlExecutionService.executeAuditedSelect(correctedSql)).thenReturn(rows);
        when(dataMaskingService.maskRows(rows)).thenReturn(maskedRows);
        when(resultAnalysisService.analyze(question, correctedSql, maskedRows, 1)).thenReturn(summary);

        QueryResultVO result = dataQueryService.query(7L, question);

        assertThat(result.sql()).isEqualTo(correctedSql);
        verify(textToSqlService).correctSql(question, failedSql, "query; bad SQL grammar [" + failedSql + "]");
        verify(queryCacheService).put(7L, question, result);
    }

    @Test
    void shouldNotCorrectConnectionFailure() {
        String question = "查询前一个客户";
        String sql = "SELECT customer_name FROM biz_customer LIMIT 5";
        SqlExecutionException connectionFailure = new SqlExecutionException(sql,
                new DataAccessResourceFailureException("connection refused"));
        when(queryCacheService.get(7L, question)).thenReturn(Optional.empty());
        when(textToSqlService.generateSql(question)).thenReturn(new SqlGenerationVO(question, sql));
        when(sqlExecutionService.executeAuditedSelect(sql)).thenThrow(connectionFailure);

        assertThatThrownBy(() -> dataQueryService.query(7L, question))
                .isSameAs(connectionFailure);
        verify(textToSqlService, never()).correctSql(anyString(), anyString(), anyString());
        verify(queryCacheService, never()).put(7L, question, null);
        ArgumentCaptor<QueryHistoryRecordCommand> historyCaptor =
                ArgumentCaptor.forClass(QueryHistoryRecordCommand.class);
        verify(queryHistoryService).recordAsync(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
                .extracting(QueryHistoryRecordCommand::generatedSql,
                        QueryHistoryRecordCommand::sqlAuditResult,
                        QueryHistoryRecordCommand::status,
                        QueryHistoryRecordCommand::errorMessage)
                .containsExactly(sql, "PASS", "FAIL", ResultCode.SQL_EXECUTION_FAILED.getMessage());
    }

    @Test
    void shouldStopAfterTwoCorrectionAttempts() {
        String question = "查询前一个客户";
        String firstSql = "SELECT bad_column_1 FROM biz_customer LIMIT 5";
        String secondSql = "SELECT bad_column_2 FROM biz_customer LIMIT 5";
        String thirdSql = "SELECT bad_column_3 FROM biz_customer LIMIT 5";
        SqlExecutionException firstFailure = badSqlGrammarFailure(firstSql);
        SqlExecutionException secondFailure = badSqlGrammarFailure(secondSql);
        SqlExecutionException thirdFailure = badSqlGrammarFailure(thirdSql);
        when(queryCacheService.get(7L, question)).thenReturn(Optional.empty());
        when(textToSqlService.generateSql(question)).thenReturn(new SqlGenerationVO(question, firstSql));
        when(sqlExecutionService.executeAuditedSelect(firstSql)).thenThrow(firstFailure);
        when(textToSqlService.correctSql(question, firstSql, firstFailure.getCause().getMessage()))
                .thenReturn(secondSql);
        when(sqlExecutionService.executeAuditedSelect(secondSql)).thenThrow(secondFailure);
        when(textToSqlService.correctSql(question, secondSql, secondFailure.getCause().getMessage()))
                .thenReturn(thirdSql);
        when(sqlExecutionService.executeAuditedSelect(thirdSql)).thenThrow(thirdFailure);

        assertThatThrownBy(() -> dataQueryService.query(7L, question))
                .isSameAs(thirdFailure);
        verify(textToSqlService).correctSql(question, firstSql, firstFailure.getCause().getMessage());
        verify(textToSqlService).correctSql(question, secondSql, secondFailure.getCause().getMessage());
    }

    @Test
    void shouldRecordReadOnlyIntentRejectionAsAuditReject() {
        String question = "删除第一个客户";
        BusinessException rejection = new BusinessException(ResultCode.READ_ONLY_QUERY_REQUIRED);
        org.mockito.Mockito.doThrow(rejection).when(queryRequestGuard).validateAndAcquire(7L, question);

        assertThatThrownBy(() -> dataQueryService.query(7L, question))
                .isSameAs(rejection);

        verifyNoInteractions(queryCacheService, textToSqlService, sqlExecutionService);
        ArgumentCaptor<QueryHistoryRecordCommand> historyCaptor =
                ArgumentCaptor.forClass(QueryHistoryRecordCommand.class);
        verify(queryHistoryService).recordAsync(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
                .extracting(QueryHistoryRecordCommand::generatedSql,
                        QueryHistoryRecordCommand::sqlAuditResult,
                        QueryHistoryRecordCommand::sqlAuditReason,
                        QueryHistoryRecordCommand::status)
                .containsExactly(null, "REJECT", ResultCode.READ_ONLY_QUERY_REQUIRED.getMessage(), "AUDIT_REJECT");
    }

    private SqlExecutionException badSqlGrammarFailure(String sql) {
        return new SqlExecutionException(sql,
                new BadSqlGrammarException("query", sql, new SQLException("Unknown column in " + sql)));
    }
}
