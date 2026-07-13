package com.aianalyst.service;

/** Controls how frequently one user may initiate an AI data query. */
public interface RateLimitService {

    /**
     * Tries to consume one query quota for a user.
     *
     * @return {@code true} when the request may proceed; otherwise {@code false}
     */
    boolean tryAcquire(Long userId);
}
