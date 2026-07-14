package com.aianalyst.service;

import com.aianalyst.dto.QueryHistoryRecordCommand;
import com.aianalyst.vo.PageResultVO;
import com.aianalyst.vo.QueryHistoryVO;

/** Query audit persistence and current-user history retrieval. */
public interface QueryHistoryService {

    /** Submits a non-blocking audit write; failures must never affect the completed query response. */
    void recordAsync(QueryHistoryRecordCommand command);

    /** Returns a page of audit records that belongs to the current user only. */
    PageResultVO<QueryHistoryVO> pageMyHistory(Long userId, long page, long size);
}
