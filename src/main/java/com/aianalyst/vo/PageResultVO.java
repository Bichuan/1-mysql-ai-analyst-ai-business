package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Standard pagination response shared by list-style APIs. */
@Schema(description = "通用分页结果")
public record PageResultVO<T>(
        @Schema(description = "当前页数据") List<T> records,
        @Schema(description = "符合条件的总记录数", example = "21") long total,
        @Schema(description = "当前页码，从 1 开始", example = "1") long page,
        @Schema(description = "每页数量", example = "10") long size,
        @Schema(description = "总页数", example = "3") long pages) {
}
