package com.aianalyst.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 查询历史使用独立线程池，避免审计落库与主查询链路争抢 Web 请求线程。
 */
@Configuration(proxyBeanMethods = false)
public class QueryHistoryExecutorConfig {

    /**
     * ThreadPoolTaskExecutor 是对 JUC ThreadPoolExecutor 的 Spring 托管封装。
     *
     * <p>队列必须有界：历史任务在异常流量下不能无限堆积并耗尽内存。队列满时使用
     * AbortPolicy，让调用方记录告警并丢弃本次非关键历史，而不是使用 CallerRunsPolicy
     * 反向阻塞用户的主查询响应。</p>
     */
    @Bean(name = "queryHistoryExecutor")
    public ThreadPoolTaskExecutor queryHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("query-history-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 应用正常关闭时尽量写完已经入队的审计任务，避免直接中断造成不必要的数据丢失。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
