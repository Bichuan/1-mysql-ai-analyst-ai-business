package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Result of a natural-language query after SQL generation, auditing, and read-only execution. */
@Schema(description = "数据查询结果")
public record QueryResultVO(
        @Schema(description = "原始自然语言问题") String question,
        @Schema(description = "审核并规范化后的只读 SQL") String sql,
        @Schema(description = "查询结果行") List<Map<String, Object>> rows,
        @Schema(description = "返回行数", example = "10") int rowCount,
        @Schema(description = "基于脱敏数据生成的 AI 业务总结") String summary,
        @Schema(description = "是否命中 Redis 查询缓存") boolean cacheHit) {
}
