package com.aianalyst.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekPropertiesTest {

    @Test
    void shouldUseConservativeTokenBudgetDefaults() {
        DeepSeekProperties properties = new DeepSeekProperties();

        assertThat(properties.getContextWindowTokens()).isEqualTo(32_768);
        assertThat(properties.getContextUsageLimit()).isEqualTo(0.80D);
        assertThat(properties.getTokenSafetyMargin()).isEqualTo(256);
        assertThat(properties.getMaxTokens()).isEqualTo(2_048);
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(25));
        assertThat(properties.getMaxRetries()).isZero();
    }

    @Test
    void shouldRejectInvalidTokenBudgetConfiguration() {
        DeepSeekProperties properties = new DeepSeekProperties();

        assertThatThrownBy(() -> properties.setContextWindowTokens(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setContextUsageLimit(1.01D))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setTokenSafetyMargin(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxTokens(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setTimeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxRetries(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxRetries(2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
