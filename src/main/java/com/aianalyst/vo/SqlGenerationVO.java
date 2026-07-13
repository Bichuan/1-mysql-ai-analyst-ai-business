package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Generated SQL is returned for review only; execution is introduced in a later audited workflow. */
@Schema(description = "SQL 生成结果")
public record SqlGenerationVO(
        @Schema(description = "原始自然语言问题") String question,
        @Schema(description = "模型生成的 SQL，仅供后续审核") String sql) {
}
