package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.DeepSeekProperties;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.TokenBudgetService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeepSeekChatServiceImplTest {

    @Test
    void shouldRejectBlankPromptBeforeCreatingModelClient() {
        DeepSeekProperties properties = new DeepSeekProperties();
        DeepSeekChatServiceImpl chatService = chatService(properties, mock(QueryMetricsService.class));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> chatService.generate("   "))
                .withMessage("prompt must not be blank");
    }

    @Test
    void shouldReportMissingApiKeyWithoutSendingNetworkRequest() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey(" ");
        DeepSeekChatServiceImpl chatService = chatService(properties, mock(QueryMetricsService.class));

        assertThatIllegalStateException()
                .isThrownBy(() -> chatService.generate("生成一条安全查询"))
                .withMessageContaining("DeepSeek API key is not configured");
    }

    @Test
    void shouldRejectOversizedPromptBeforeCreatingModelClient() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setContextWindowTokens(100);
        properties.setContextUsageLimit(0.8D);
        properties.setMaxTokens(20);
        properties.setTokenSafetyMargin(10);
        QueryMetricsService metricsService = mock(QueryMetricsService.class);
        DeepSeekChatServiceImpl chatService = chatService(properties, metricsService);

        assertThatThrownBy(() -> chatService.generate("a".repeat(204)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.CONTEXT_WINDOW_EXCEEDED);

        verify(metricsService).recordTokenBudgetRejected();
    }

    private DeepSeekChatServiceImpl chatService(DeepSeekProperties properties,
                                                QueryMetricsService metricsService) {
        TokenBudgetService budgetService = new TokenBudgetServiceImpl(
                properties, new ConservativeTokenEstimator());
        return new DeepSeekChatServiceImpl(properties, budgetService, metricsService);
    }
}
