package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** Summary view of one query audit record. The stored result JSON is intentionally not returned here. */
@Schema(description = "当前用户的一条查询审计记录摘要")
public record QueryHistoryVO(
        @Schema(description = "历史记录 ID", example = "1001") Long id,
        @Schema(description = "用户原始自然语言问题", example = "查询今年销售额最高的10个客户") String question,
        @Schema(description = "审核后的只读 SQL；生成前失败时可能为空") String sql,
        @Schema(description = "SQL 审核结果", example = "PASS") String sqlAuditResult,
        @Schema(description = "审核拒绝原因；通过时为空") String sqlAuditReason,
        @Schema(description = "基于脱敏结果生成的 AI 总结") String summary,
        @Schema(description = "SQL 执行耗时，单位为毫秒", example = "35") Integer executionTime,
        @Schema(description = "执行状态：SUCCESS、AUDIT_REJECT 或 FAIL", example = "SUCCESS") String status,
        @Schema(description = "失败时返回的安全错误信息") String errorMessage,
        @Schema(description = "记录创建时间", example = "2026-07-15T10:32:02") LocalDateTime createdAt) {
}
