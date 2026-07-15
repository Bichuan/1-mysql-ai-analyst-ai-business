package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal application availability response used during local development and deployment checks.
 */
@Schema(description = "应用基础健康状态")
public record HealthVO(
        @Schema(description = "应用状态", example = "UP") String status,
        @Schema(description = "应用名称", example = "ai-data-analyst") String application) {
}
