package com.aianalyst.service;

import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.vo.PageResultVO;
import com.aianalyst.vo.QueryHistoryVO;

import java.util.concurrent.CompletableFuture;

/** Query audit persistence and current-user history retrieval. */
public interface QueryHistoryService {

    /**
     * Submits a non-blocking audit write. The future yields the generated history id, or null when
     * persistence is unavailable; failures must never affect the completed query response.
     */
    CompletableFuture<Long> recordAsync(QueryHistoryRecordCommand command);

    /** Returns a page of audit records that belongs to the current user only. */
    PageResultVO<QueryHistoryVO> pageMyHistory(Long userId, long page, long size);
}
