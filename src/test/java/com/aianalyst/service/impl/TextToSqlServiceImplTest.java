package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.DeepSeekChatService;
import com.aianalyst.service.QueryIntentSafetyValidator;
import com.aianalyst.service.QuerySemanticValidator;
import com.aianalyst.service.RateLimitService;
import com.aianalyst.service.SqlAuditService;
import com.aianalyst.service.TextToSqlPromptBuilder;
import com.aianalyst.vo.SqlGenerationVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TextToSqlServiceImplTest {

    @Mock
    private QueryIntentSafetyValidator queryIntentSafetyValidator;

    @Mock
    private QuerySemanticValidator querySemanticValidator;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private TextToSqlPromptBuilder promptBuilder;

    @Mock
    private DeepSeekChatService deepSeekChatService;

    @Mock
    private SqlAuditService sqlAuditService;

    @InjectMocks
    private TextToSqlServiceImpl textToSqlService;

    @Test
    void shouldStripSqlMarkdownFence() {
        when(rateLimitService.tryAcquire(1L)).thenReturn(true);
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(deepSeekChatService.generate("prompt")).thenReturn("```sql\nSELECT id FROM biz_customer LIMIT 10;\n```");
        when(sqlAuditService.auditAndNormalize("SELECT id FROM biz_customer LIMIT 10;"))
                .thenReturn("SELECT id FROM biz_customer LIMIT 10");

        SqlGenerationVO result = textToSqlService.generateSql(1L, "查询客户");

        assertThat(result.sql()).isEqualTo("SELECT id FROM biz_customer LIMIT 10");
    }

    @Test
    void shouldRejectRequestWhenRateLimitIsExceeded() {
        when(rateLimitService.tryAcquire(1L)).thenReturn(false);

        assertThatThrownBy(() -> textToSqlService.generateSql(1L, "查询客户"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldRejectInvalidSemanticCountBeforeRateLimitAndModelCall() {
        String question = "查询今年销售额最高的-1个客户";
        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "查询数量必须是 1 到 1000 之间的整数"))
                .when(querySemanticValidator).validate(question);

        assertThatThrownBy(() -> textToSqlService.generateSql(1L, question))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.PARAM_ERROR);
        verifyNoInteractions(rateLimitService, promptBuilder, deepSeekChatService, sqlAuditService);
    }

    @Test
    void shouldRejectWriteIntentBeforeRateLimitAndModelCall() {
        String question = "删除第1个客户的订单";
        doThrow(new BusinessException(ResultCode.READ_ONLY_QUERY_REQUIRED))
                .when(queryIntentSafetyValidator).validateReadOnlyIntent(question);

        assertThatThrownBy(() -> textToSqlService.generateSql(1L, question))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.READ_ONLY_QUERY_REQUIRED);
        verifyNoInteractions(querySemanticValidator, rateLimitService, promptBuilder,
                deepSeekChatService, sqlAuditService);
    }

    @Test
    void shouldAuditCorrectedSqlWithoutApplyingAnotherRateLimitToken() {
        String question = "查询客户";
        String failedSql = "SELECT customer_nam FROM biz_customer LIMIT 5";
        String error = "Unknown column 'customer_nam'";
        when(promptBuilder.buildCorrection(question, failedSql, error)).thenReturn("correction prompt");
        when(deepSeekChatService.generate("correction prompt"))
                .thenReturn("```sql\nSELECT customer_name FROM biz_customer LIMIT 5\n```");
        when(sqlAuditService.auditAndNormalize("SELECT customer_name FROM biz_customer LIMIT 5"))
                .thenReturn("SELECT customer_name FROM biz_customer LIMIT 5");

        String correctedSql = textToSqlService.correctSql(question, failedSql, error);

        assertThat(correctedSql).isEqualTo("SELECT customer_name FROM biz_customer LIMIT 5");
    }
}
