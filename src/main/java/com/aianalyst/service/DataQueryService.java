package com.aianalyst.service;

import com.aianalyst.vo.QueryResultVO;

/** Orchestrates the full natural-language to read-only business-query workflow. */
public interface DataQueryService {

    /** Backward-compatible single-turn entry; a new conversation id is created automatically. */
    default QueryResultVO query(Long userId, String question) {
        return query(userId, null, question);
    }

    QueryResultVO query(Long userId, String conversationId, String question);
}
