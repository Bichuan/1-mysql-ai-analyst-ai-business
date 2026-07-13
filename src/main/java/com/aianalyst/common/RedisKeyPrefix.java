package com.aianalyst.common;

/** Redis key prefixes owned by this application. */
public final class RedisKeyPrefix {

    public static final String QUERY_RATE_LIMIT = "rate_limit:";
    public static final String QUERY_CACHE = "query_cache:";

    private RedisKeyPrefix() {
    }
}
