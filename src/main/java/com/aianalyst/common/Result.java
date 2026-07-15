package com.aianalyst.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard response envelope for all REST APIs.
 *
 * @param code application-level status code
 * @param message user-facing message
 * @param data response payload
 * @param <T> response payload type
 */
@Schema(description = "统一 API 响应结构；HTTP 状态码表示协议结果，code 表示业务结果")
public record Result<T>(
        @Schema(description = "业务状态码，0 表示成功", example = "0") int code,
        @Schema(description = "面向客户端的结果说明", example = "success") String message,
        @Schema(description = "具体响应数据；失败时通常为 null") T data) {

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return error(resultCode, resultCode.getMessage());
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String message, T data) {
        return new Result<>(resultCode.getCode(), message, data);
    }
}
