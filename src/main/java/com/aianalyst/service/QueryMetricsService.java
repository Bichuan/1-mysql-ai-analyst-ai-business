package com.aianalyst.service;

/** Metrics emitted by the natural-language query workflow. */
public interface QueryMetricsService {

    void recordCacheHit();

    void recordCacheMiss();

    void recordCacheFallback();

    void recordHistoryTaskRejected();

    void recordModelPromptTokens(int estimatedTokens);

    void recordTokenCompression();

    void recordTokenBudgetRejected();

    void recordQueryDuration(long durationNanos);
}
