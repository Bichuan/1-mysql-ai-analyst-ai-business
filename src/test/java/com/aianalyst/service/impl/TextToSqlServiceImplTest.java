package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.dto.SqlGenerationOutcome;
import com.aianalyst.service.ModelResilienceGateway;
import com.aianalyst.service.SqlAuditService;
import com.aianalyst.service.prompt.TextToSqlPromptBuilder;
import com.aianalyst.vo.SqlGenerationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextToSqlServiceImplTest {

    @Mock
    private TextToSqlPromptBuilder promptBuilder;

    @Mock
    private ModelResilienceGateway modelResilienceGateway;

    @Mock
    private SqlAuditService sqlAuditService;

    @InjectMocks
    private TextToSqlServiceImpl textToSqlService;

    @Test
    void shouldStripSqlMarkdownFence() {
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(modelResilienceGateway.generateSql("prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture(
                        "```sql\nSELECT id FROM biz_customer LIMIT 10;\n```"));
        when(sqlAuditService.auditAndNormalize("SELECT id FROM biz_customer LIMIT 10;"))
                .thenReturn("SELECT id FROM biz_customer LIMIT 10");

        SqlGenerationVO result = textToSqlService.generateSql("查询客户");

        assertThat(result.sql()).isEqualTo("SELECT id FROM biz_customer LIMIT 10");
    }

    @Test
    void shouldRejectEmptyModelResponse() {
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(modelResilienceGateway.generateSql("prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture("   "));

        assertThatThrownBy(() -> textToSqlService.generateSql("查询客户"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.BUSINESS_ERROR);
    }

    @Test
    void shouldStopBeforeSqlAuditWhenModelStageIsUnavailable() {
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(modelResilienceGateway.generateSql("prompt")).thenReturn(
                java.util.concurrent.CompletableFuture.failedFuture(
                        new java.util.concurrent.TimeoutException("timeout")));

        assertThatThrownBy(() -> textToSqlService.generateSql("查询客户"))
                .isInstanceOf(com.aianalyst.common.ModelCallException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.MODEL_SERVICE_UNAVAILABLE);

        verify(sqlAuditService, never()).auditAndNormalize(anyString());
    }

    @Test
    void shouldAuditCorrectedSqlWithoutApplyingAnotherRateLimitToken() {
        String question = "查询客户";
        String failedSql = "SELECT customer_nam FROM biz_customer LIMIT 5";
        String error = "Unknown column 'customer_nam'";
        when(promptBuilder.buildCorrection(question, failedSql, error)).thenReturn("correction prompt");
        when(modelResilienceGateway.generateSql("correction prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        "```sql\nSELECT customer_name FROM biz_customer LIMIT 5\n```"));
        when(sqlAuditService.auditAndNormalize("SELECT customer_name FROM biz_customer LIMIT 5"))
                .thenReturn("SELECT customer_name FROM biz_customer LIMIT 5");

        String correctedSql = textToSqlService.correctSql(question, failedSql, error);

        assertThat(correctedSql).isEqualTo("SELECT customer_name FROM biz_customer LIMIT 5");
    }

    @Test
    void shouldCorrectMultiStatementModelOutputOnceAndAuditAgain() {
        String question = "查询去年华中销售额最高的10个客户";
        String candidate = "SELECT id FROM biz_customer; SELECT id FROM biz_order;";
        String corrected = "SELECT id FROM biz_customer WHERE region = '华中' LIMIT 10";
        BusinessException auditFailure = new BusinessException(
                ResultCode.SQL_AUDIT_FAILED, "只允许单条 SQL 语句");
        when(promptBuilder.build(question)).thenReturn("generation prompt");
        when(modelResilienceGateway.generateSql("generation prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(candidate));
        when(sqlAuditService.auditAndNormalize(candidate)).thenThrow(auditFailure);
        when(promptBuilder.buildAuditCorrection(
                question, candidate, "只允许单条 SQL 语句"))
                .thenReturn("audit correction prompt");
        when(modelResilienceGateway.generateSql("audit correction prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(corrected));
        when(sqlAuditService.auditAndNormalize(corrected)).thenReturn(corrected);

        SqlGenerationOutcome outcome = textToSqlService.generateSqlWithAuditRecovery(question);

        assertThat(outcome.result().sql()).isEqualTo(corrected);
        assertThat(outcome.correctionAttemptsUsed()).isEqualTo(1);
        verify(sqlAuditService).auditAndNormalize(candidate);
        verify(sqlAuditService).auditAndNormalize(corrected);
    }

    @Test
    void shouldNotCorrectSecurityAuditRejection() {
        String question = "查询客户";
        String candidate = "SELECT * FROM sys_user";
        BusinessException auditFailure = new BusinessException(
                ResultCode.SQL_AUDIT_FAILED, "引用了未授权的数据表：sys_user");
        when(promptBuilder.build(question)).thenReturn("generation prompt");
        when(modelResilienceGateway.generateSql("generation prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(candidate));
        when(sqlAuditService.auditAndNormalize(candidate)).thenThrow(auditFailure);

        assertThatThrownBy(() -> textToSqlService.generateSqlWithAuditRecovery(question))
                .isSameAs(auditFailure);

        verify(promptBuilder, never()).buildAuditCorrection(
                anyString(), anyString(), anyString());
    }

    @Test
    void shouldStopWhenTheSingleAuditCorrectionStillFails() {
        String question = "查询客户";
        String firstCandidate = "SELECT id FROM biz_customer; SELECT id FROM biz_order;";
        String secondCandidate = "SELECT customer_name FROM biz_customer; SELECT amount FROM biz_order;";
        BusinessException firstFailure = new BusinessException(
                ResultCode.SQL_AUDIT_FAILED, "只允许单条 SQL 语句");
        BusinessException secondFailure = new BusinessException(
                ResultCode.SQL_AUDIT_FAILED, "只允许单条 SQL 语句");
        when(promptBuilder.build(question)).thenReturn("generation prompt");
        when(modelResilienceGateway.generateSql("generation prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(firstCandidate));
        when(sqlAuditService.auditAndNormalize(firstCandidate)).thenThrow(firstFailure);
        when(promptBuilder.buildAuditCorrection(
                question, firstCandidate, "只允许单条 SQL 语句"))
                .thenReturn("audit correction prompt");
        when(modelResilienceGateway.generateSql("audit correction prompt"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(secondCandidate));
        when(sqlAuditService.auditAndNormalize(secondCandidate)).thenThrow(secondFailure);

        assertThatThrownBy(() -> textToSqlService.generateSqlWithAuditRecovery(question))
                .isSameAs(secondFailure);

        verify(modelResilienceGateway).generateSql("audit correction prompt");
    }
}
