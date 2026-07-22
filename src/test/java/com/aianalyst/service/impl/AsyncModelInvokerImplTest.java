package com.aianalyst.service.impl;

import com.aianalyst.config.LlmAsyncExecutorConfig;
import com.aianalyst.service.AsyncModelInvoker;
import com.aianalyst.service.DeepSeekChatService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncModelInvokerImplTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LlmAsyncExecutorConfig.class, TestConfiguration.class);

    @Test
    void shouldRouteCoreAndAnalysisCallsToIndependentExecutors() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AsyncModelInvoker invoker = context.getBean(AsyncModelInvoker.class);

            assertThat(invoker.invokeCore("core").get(2, TimeUnit.SECONDS))
                    .startsWith("llm-core-");
            assertThat(invoker.invokeAnalysis("analysis").get(2, TimeUnit.SECONDS))
                    .startsWith("llm-analysis-");
        });
    }

    @Test
    void shouldRejectSaturatedCorePoolWithoutBlockingTheAnalysisPool() {
        contextRunner
                .withPropertyValues(
                        "app.async.llm-core.core-pool-size=1",
                        "app.async.llm-core.max-pool-size=1",
                        "app.async.llm-core.queue-capacity=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AsyncModelInvoker invoker = context.getBean(AsyncModelInvoker.class);
                    BlockingDeepSeekChatService chatService =
                            context.getBean(BlockingDeepSeekChatService.class);

                    CompletableFuture<String> blocked = invoker.invokeCore("block-core");
                    assertThat(chatService.started.await(2, TimeUnit.SECONDS)).isTrue();
                    try {
                        assertThat(invoker.invokeAnalysis("analysis").get(2, TimeUnit.SECONDS))
                                .startsWith("llm-analysis-");
                        assertThatThrownBy(() -> invoker.invokeCore("rejected"))
                                .isInstanceOf(TaskRejectedException.class);
                    } finally {
                        chatService.release.countDown();
                    }
                    assertThat(blocked.get(2, TimeUnit.SECONDS)).startsWith("llm-core-");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        BlockingDeepSeekChatService deepSeekChatService() {
            return new BlockingDeepSeekChatService();
        }

        @Bean
        AsyncModelInvoker asyncModelInvoker(DeepSeekChatService deepSeekChatService) {
            return new AsyncModelInvokerImpl(deepSeekChatService);
        }
    }

    static class BlockingDeepSeekChatService implements DeepSeekChatService {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String generate(String prompt) {
            if ("block-core".equals(prompt)) {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test did not release the blocked model call");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("model call interrupted", exception);
                }
            }
            return Thread.currentThread().getName();
        }
    }
}
