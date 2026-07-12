package com.aianalyst.common;

/**
 * Application-level result codes returned by every API endpoint.
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40001, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    FORBIDDEN(40300, "无访问权限"),
    NOT_FOUND(40400, "请求资源不存在"),
    BUSINESS_ERROR(50001, "业务处理失败"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
