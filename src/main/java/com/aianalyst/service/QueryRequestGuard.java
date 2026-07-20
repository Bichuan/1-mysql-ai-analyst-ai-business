package com.aianalyst.service;

/**
 * Shared pre-check for every user-initiated AI query request.
 * It is intentionally executed before a cache lookup so cached requests cannot bypass rate limiting.
 */
public interface QueryRequestGuard {

    /** Deterministic safety checks without consuming a rate-limit token. */
    void validate(String question);

    /** Consumes exactly one token for one user-initiated HTTP query. */
    void acquire(Long userId);

    default void validateAndAcquire(Long userId, String question) {
        validate(question);
        acquire(userId);
    }
}
