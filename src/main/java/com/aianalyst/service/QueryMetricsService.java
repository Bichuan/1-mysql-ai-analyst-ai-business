package com.aianalyst.service;

/** Metrics emitted by the natural-language query workflow. */
public interface QueryMetricsService {

    void recordCacheHit();

    void recordCacheMiss();

    void recordCacheFallback();

    void recordHistoryTaskRejected();

    void recordQueryDuration(long durationNanos);
}
