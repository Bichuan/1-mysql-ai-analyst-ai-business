package com.aianalyst.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** Dedicated bounded executors for model work and future asynchronous query orchestration. */
@EnableAsync
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AsyncExecutorProperties.class)
public class LlmAsyncExecutorConfig {

    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    @Bean(name = "llmCoreExecutor")
    public ThreadPoolTaskExecutor llmCoreExecutor(AsyncExecutorProperties properties) {
        properties.validate();
        return createExecutor(properties.getLlmCore());
    }

    @Bean(name = "llmAnalysisExecutor")
    public ThreadPoolTaskExecutor llmAnalysisExecutor(AsyncExecutorProperties properties) {
        properties.validate();
        return createExecutor(properties.getLlmAnalysis());
    }

    @Bean(name = "queryOrchestrationExecutor")
    public ThreadPoolTaskExecutor queryOrchestrationExecutor(AsyncExecutorProperties properties) {
        properties.validate();
        return createExecutor(properties.getQueryOrchestration());
    }

    private ThreadPoolTaskExecutor createExecutor(AsyncExecutorProperties.Pool properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setTaskDecorator(new MdcTaskDecorator());
        // Saturation must be visible to the caller; CallerRunsPolicy would block the request thread.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
