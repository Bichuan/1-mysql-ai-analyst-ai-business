package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.DeepSeekChatService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextToSqlServiceImplTest {

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
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(deepSeekChatService.generate("prompt")).thenReturn("```sql\nSELECT id FROM biz_customer LIMIT 10;\n```");
        when(sqlAuditService.auditAndNormalize("SELECT id FROM biz_customer LIMIT 10;"))
                .thenReturn("SELECT id FROM biz_customer LIMIT 10");

        SqlGenerationVO result = textToSqlService.generateSql("查询客户");

        assertThat(result.sql()).isEqualTo("SELECT id FROM biz_customer LIMIT 10");
    }

    @Test
    void shouldRejectEmptyModelResponse() {
        when(promptBuilder.build("查询客户")).thenReturn("prompt");
        when(deepSeekChatService.generate("prompt")).thenReturn("   ");

        assertThatThrownBy(() -> textToSqlService.generateSql("查询客户"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.BUSINESS_ERROR);
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
