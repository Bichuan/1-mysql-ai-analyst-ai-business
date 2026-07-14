package com.aianalyst.vo;

import java.time.LocalDateTime;

/** Summary view of one query audit record. The stored result JSON is intentionally not returned here. */
public record QueryHistoryVO(
        Long id,
        String question,
        String sql,
        String sqlAuditResult,
        String sqlAuditReason,
        String summary,
        Integer executionTime,
        String status,
        String errorMessage,
        LocalDateTime createdAt) {
}
