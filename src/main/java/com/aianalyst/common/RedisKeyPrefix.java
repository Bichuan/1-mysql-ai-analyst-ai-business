package com.aianalyst.common;

/** Redis key prefixes owned by this application. */
public final class RedisKeyPrefix {

    public static final String QUERY_RATE_LIMIT = "rate_limit:";
    /** v1 allows a later cache payload/key migration without reading incompatible old values. */
    public static final String QUERY_CACHE = "query_cache:v1:";
    /** Active conversation metadata and recent turns. */
    public static final String CONVERSATION = "conversation:v1:";

    private RedisKeyPrefix() {
    }
}
