package com.aianalyst.service.impl;

import com.aianalyst.service.QueryMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 将查询链路的关键状态暴露给 Spring Boot Actuator / Micrometer。
 * 指标只保存计数和耗时，不携带问题、SQL、用户 ID 或查询结果。
 */
@Service
public class MicrometerQueryMetricsService implements QueryMetricsService {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheFallbackCounter;
    private final Counter historyTaskRejectedCounter;
    private final Timer queryDurationTimer;

    public MicrometerQueryMetricsService(MeterRegistry meterRegistry,
                                         @Qualifier("queryHistoryExecutor") ThreadPoolTaskExecutor historyExecutor) {
        this.cacheHitCounter = Counter.builder("ai.query.cache.hit").register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ai.query.cache.miss").register(meterRegistry);
        this.cacheFallbackCounter = Counter.builder("ai.query.cache.fallback").register(meterRegistry);
        this.historyTaskRejectedCounter = Counter.builder("ai.query.history.task.rejected").register(meterRegistry);
        this.queryDurationTimer = Timer.builder("ai.query.request.duration").register(meterRegistry);

        // Gauge 直接读取线程池瞬时状态，不需要额外维护容易失真的计数器。
        meterRegistry.gauge("ai.query.history.executor.active", historyExecutor,
                executor -> executor.getActiveCount());
        meterRegistry.gauge("ai.query.history.executor.queue.size", historyExecutor,
                executor -> executor.getThreadPoolExecutor().getQueue().size());
    }

    @Override
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    @Override
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    @Override
    public void recordCacheFallback() {
        cacheFallbackCounter.increment();
    }

    @Override
    public void recordHistoryTaskRejected() {
        historyTaskRejectedCounter.increment();
    }

    @Override
    public void recordQueryDuration(long durationNanos) {
        queryDurationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
