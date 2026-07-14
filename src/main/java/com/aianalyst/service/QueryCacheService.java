package com.aianalyst.service;

import com.aianalyst.vo.QueryResultVO;

import java.util.Optional;

/** Redis-backed cache for completed, masked natural-language query results. */
public interface QueryCacheService {

    Optional<QueryResultVO> get(Long userId, String question);

    void put(Long userId, String question, QueryResultVO result);
}
