package com.aianalyst.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAsyncExecutorConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LlmAsyncExecutorConfig.class);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldCreateThreeIndependentBoundedExecutors() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ThreadPoolTaskExecutor core = context.getBean("llmCoreExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor analysis = context.getBean(
                    "llmAnalysisExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor orchestration = context.getBean(
                    "queryOrchestrationExecutor", ThreadPoolTaskExecutor.class);

            assertExecutor(core, 4, 6, 20, "llm-core-");
            assertExecutor(analysis, 1, 2, 10, "llm-analysis-");
            assertExecutor(orchestration, 4, 8, 50, "query-orchestration-");
        });
    }

    @Test
    void shouldBindPoolOverridesAndPropagateMdcWithoutLeakingIt() {
        contextRunner
                .withPropertyValues(
                        "app.async.llm-core.core-pool-size=2",
                        "app.async.llm-core.max-pool-size=3",
                        "app.async.llm-core.queue-capacity=4",
                        "app.async.llm-core.thread-name-prefix=test-llm-core-")
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean(
                            "llmCoreExecutor", ThreadPoolTaskExecutor.class);
                    assertExecutor(executor, 2, 3, 4, "test-llm-core-");

                    MDC.put("requestId", "request-12345678");
                    CompletableFuture<String> propagated = new CompletableFuture<>();
                    executor.execute(() -> propagated.complete(MDC.get("requestId")));
                    assertThat(propagated.get(2, TimeUnit.SECONDS)).isEqualTo("request-12345678");

                    MDC.clear();
                    CompletableFuture<String> cleared = new CompletableFuture<>();
                    executor.execute(() -> cleared.complete(MDC.get("requestId")));
                    assertThat(cleared.get(2, TimeUnit.SECONDS)).isNull();
                });
    }

    private void assertExecutor(ThreadPoolTaskExecutor executor,
                                int corePoolSize,
                                int maxPoolSize,
                                int queueCapacity,
                                String threadNamePrefix) {
        assertThat(executor.getCorePoolSize()).isEqualTo(corePoolSize);
        assertThat(executor.getMaxPoolSize()).isEqualTo(maxPoolSize);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(queueCapacity);
        assertThat(executor.getThreadNamePrefix()).isEqualTo(threadNamePrefix);
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(java.util.concurrent.ThreadPoolExecutor.AbortPolicy.class);
    }
}
