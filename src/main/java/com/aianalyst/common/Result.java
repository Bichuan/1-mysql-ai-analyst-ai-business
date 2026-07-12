package com.aianalyst.common;

/**
 * Standard response envelope for all REST APIs.
 *
 * @param code application-level status code
 * @param message user-facing message
 * @param data response payload
 * @param <T> response payload type
 */
public record Result<T>(int code, String message, T data) {

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
