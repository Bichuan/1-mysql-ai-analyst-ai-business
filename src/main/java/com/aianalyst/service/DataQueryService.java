package com.aianalyst.service;

import com.aianalyst.vo.QueryResultVO;

/** Orchestrates the full natural-language to read-only business-query workflow. */
public interface DataQueryService {

    QueryResultVO query(Long userId, String question);
}
