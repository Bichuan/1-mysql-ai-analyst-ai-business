package com.aianalyst.service.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ModelExecutorMetricsTest {

    @Test
    void shouldPublishAllBoundedExecutorGaugesWithStablePoolTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor core = executor(2, 3);
        ThreadPoolTaskExecutor analysis = executor(1, 2);
        ThreadPoolTaskExecutor orchestration = executor(2, 4);
        try {
            new ModelExecutorMetrics(registry, core, analysis, orchestration);

            assertPoolMetrics(registry, "core", 3);
            assertPoolMetrics(registry, "analysis", 2);
            assertPoolMetrics(registry, "orchestration", 4);
        } finally {
            core.shutdown();
            analysis.shutdown();
            orchestration.shutdown();
            registry.close();
        }
    }

    private ThreadPoolTaskExecutor executor(int corePoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }

    private void assertPoolMetrics(SimpleMeterRegistry registry,
                                   String poolName,
                                   int queueCapacity) {
        assertThat(registry.get("ai.model.executor.active")
                .tag("pool", poolName).gauge().value()).isZero();
        assertThat(registry.get("ai.model.executor.pool.size")
                .tag("pool", poolName).gauge().value()).isZero();
        assertThat(registry.get("ai.model.executor.queue.size")
                .tag("pool", poolName).gauge().value()).isZero();
        assertThat(registry.get("ai.model.executor.queue.remaining")
                .tag("pool", poolName).gauge().value()).isEqualTo(queueCapacity);
    }
}
