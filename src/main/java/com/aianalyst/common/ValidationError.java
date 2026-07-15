package com.aianalyst.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One field-level validation failure returned to the client.
 */
@Schema(description = "单个字段的参数校验错误")
public record ValidationError(
        @Schema(description = "字段名", example = "question") String field,
        @Schema(description = "校验失败原因", example = "问题不能为空") String message) {
}
