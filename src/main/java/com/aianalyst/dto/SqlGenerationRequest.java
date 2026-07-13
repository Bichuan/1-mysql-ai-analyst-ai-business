package com.aianalyst.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Natural-language request for generating, but not executing, a business SQL statement. */
@Schema(description = "自然语言 SQL 生成请求")
public record SqlGenerationRequest(
        @Schema(description = "业务查询问题", example = "查询今年销售额最高的10个客户")
        @NotBlank(message = "查询问题不能为空")
        @Size(max = 500, message = "查询问题长度不能超过500个字符")
        String question) {
}
