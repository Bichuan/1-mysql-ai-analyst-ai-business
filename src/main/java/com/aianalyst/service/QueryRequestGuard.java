package com.aianalyst.service;

/**
 * Shared pre-check for every user-initiated AI query request.
 * It is intentionally executed before a cache lookup so cached requests cannot bypass rate limiting.
 */
public interface QueryRequestGuard {

    void validateAndAcquire(Long userId, String question);
}
