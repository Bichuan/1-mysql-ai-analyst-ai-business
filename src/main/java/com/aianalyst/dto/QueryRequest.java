package com.aianalyst.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for the complete natural-language to data-query workflow. */
@Schema(description = "自然语言数据查询请求")
public record QueryRequest(
        @Schema(description = "业务查询问题", example = "查询今年销售额最高的10个客户")
        @NotBlank(message = "查询问题不能为空")
        @Size(max = 500, message = "查询问题长度不能超过500个字符")
        String question) {
}
