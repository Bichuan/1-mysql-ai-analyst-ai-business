package com.aianalyst.service.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerQueryMetricsServiceTest {

    @Test
    void shouldExposeQueryAndThreadPoolMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.initialize();
        try {
            MicrometerQueryMetricsService service = new MicrometerQueryMetricsService(meterRegistry, executor);

            service.recordCacheHit();
            service.recordCacheMiss();
            service.recordCacheFallback();
            service.recordHistoryTaskRejected();
            service.recordModelPromptTokens(321);
            service.recordTokenCompression();
            service.recordTokenBudgetRejected();
            service.recordQueryDuration(TimeUnit.MILLISECONDS.toNanos(12));

            assertThat(meterRegistry.get("ai.query.cache.hit").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.query.cache.miss").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.query.cache.fallback").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.query.history.task.rejected").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.model.prompt.tokens.estimated").summary().totalAmount())
                    .isEqualTo(321.0);
            assertThat(meterRegistry.get("ai.model.token.budget.compression").counter().count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.model.token.budget.rejected").counter().count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.get("ai.query.request.duration").timer().count()).isEqualTo(1);
            assertThat(meterRegistry.get("ai.query.history.executor.active").gauge().value()).isZero();
        } finally {
            executor.shutdown();
            meterRegistry.close();
        }
    }
}
