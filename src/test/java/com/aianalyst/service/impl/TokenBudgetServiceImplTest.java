package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.DeepSeekProperties;
import com.aianalyst.dto.TokenBudgetAssessment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBudgetServiceImplTest {

    @Test
    void shouldIncludeOutputReserveAndMarginInTheEightyPercentThreshold() {
        TokenBudgetServiceImpl service = service(20, 10);

        TokenBudgetAssessment atThreshold = service.assess("a".repeat(200));
        TokenBudgetAssessment overThreshold = service.assess("a".repeat(204));

        assertThat(atThreshold.thresholdTokens()).isEqualTo(80);
        assertThat(atThreshold.maxPromptTokens()).isEqualTo(50);
        assertThat(atThreshold.exceedsLimit()).isFalse();
        assertThat(overThreshold.exceedsLimit()).isTrue();
    }

    @Test
    void shouldRejectPromptThatExceedsTheHardBudget() {
        TokenBudgetServiceImpl service = service(20, 10);

        assertThatThrownBy(() -> service.requireWithinBudget("a".repeat(204)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.CONTEXT_WINDOW_EXCEEDED);
    }

    @Test
    void shouldRejectConfigurationWithNoRoomForInput() {
        TokenBudgetServiceImpl service = service(70, 10);

        assertThatThrownBy(() -> service.assess("查询"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token budget is invalid");
    }

    private TokenBudgetServiceImpl service(int outputTokens, int safetyMargin) {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setContextWindowTokens(100);
        properties.setContextUsageLimit(0.8D);
        properties.setMaxTokens(outputTokens);
        properties.setTokenSafetyMargin(safetyMargin);
        return new TokenBudgetServiceImpl(properties, new ConservativeTokenEstimator());
    }
}
