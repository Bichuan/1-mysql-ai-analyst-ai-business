package com.aianalyst.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncExecutorPropertiesTest {

    @Test
    void shouldUseBoundedPoolDefaults() {
        AsyncExecutorProperties properties = new AsyncExecutorProperties();

        assertThat(properties.getLlmCore())
                .extracting(
                        AsyncExecutorProperties.Pool::getCorePoolSize,
                        AsyncExecutorProperties.Pool::getMaxPoolSize,
                        AsyncExecutorProperties.Pool::getQueueCapacity,
                        AsyncExecutorProperties.Pool::getThreadNamePrefix)
                .containsExactly(4, 6, 20, "llm-core-");
        assertThat(properties.getLlmAnalysis())
                .extracting(
                        AsyncExecutorProperties.Pool::getCorePoolSize,
                        AsyncExecutorProperties.Pool::getMaxPoolSize,
                        AsyncExecutorProperties.Pool::getQueueCapacity)
                .containsExactly(1, 2, 10);
        assertThat(properties.getQueryOrchestration())
                .extracting(
                        AsyncExecutorProperties.Pool::getCorePoolSize,
                        AsyncExecutorProperties.Pool::getMaxPoolSize,
                        AsyncExecutorProperties.Pool::getQueueCapacity)
                .containsExactly(4, 8, 50);
    }

    @Test
    void shouldRejectUnsafePoolConfiguration() {
        AsyncExecutorProperties properties = new AsyncExecutorProperties();

        properties.getLlmCore().setCorePoolSize(7);
        properties.getLlmCore().setMaxPoolSize(6);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-pool-size");

        properties.getLlmCore().setMaxPoolSize(7);
        properties.getLlmCore().setQueueCapacity(-1);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue-capacity");
    }
}
