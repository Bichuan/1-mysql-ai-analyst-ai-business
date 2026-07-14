package com.aianalyst.dto;

import java.util.List;
import java.util.Map;

/**
 * 写入查询审计历史的内部命令对象。
 * {@code maskedRows} 只能由脱敏服务处理后的数据填充，禁止传入原始查询结果。
 */
public record QueryHistoryRecordCommand(
        Long userId,
        String question,
        String generatedSql,
        String sqlAuditResult,
        String sqlAuditReason,
        List<Map<String, Object>> maskedRows,
        String aiSummary,
        Integer executionTime,
        String status,
        String errorMessage) {

    public QueryHistoryRecordCommand {
        maskedRows = maskedRows == null ? null : List.copyOf(maskedRows);
    }
}
