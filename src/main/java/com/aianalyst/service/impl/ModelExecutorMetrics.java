package com.aianalyst.service.impl;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** Low-cardinality gauges for the three bounded executors used by the model workflow. */
@Component
public class ModelExecutorMetrics {

    public ModelExecutorMetrics(MeterRegistry meterRegistry,
                                @Qualifier("llmCoreExecutor") ThreadPoolTaskExecutor coreExecutor,
                                @Qualifier("llmAnalysisExecutor") ThreadPoolTaskExecutor analysisExecutor,
                                @Qualifier("queryOrchestrationExecutor")
                                ThreadPoolTaskExecutor orchestrationExecutor) {
        bind(meterRegistry, coreExecutor, "core");
        bind(meterRegistry, analysisExecutor, "analysis");
        bind(meterRegistry, orchestrationExecutor, "orchestration");
    }

    private void bind(MeterRegistry registry,
                      ThreadPoolTaskExecutor executor,
                      String poolName) {
        Gauge.builder("ai.model.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active tasks in a bounded model-workflow executor")
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder("ai.model.executor.pool.size", executor,
                        value -> value.getThreadPoolExecutor().getPoolSize())
                .description("Current worker count in a bounded model-workflow executor")
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder("ai.model.executor.queue.size", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Queued tasks in a bounded model-workflow executor")
                .tag("pool", poolName)
                .register(registry);
        Gauge.builder("ai.model.executor.queue.remaining", executor,
                        value -> value.getThreadPoolExecutor().getQueue().remainingCapacity())
                .description("Remaining queue capacity in a bounded model-workflow executor")
                .tag("pool", poolName)
                .register(registry);
    }
}
