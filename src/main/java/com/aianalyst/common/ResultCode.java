package com.aianalyst.common;

/**
 * Application-level result codes returned by every API endpoint.
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(40001, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    FORBIDDEN(40300, "无访问权限"),
    TOO_MANY_REQUESTS(42900, "查询过于频繁，请稍后再试"),
    SQL_AUDIT_FAILED(40002, "SQL 安全审核未通过"),
    READ_ONLY_QUERY_REQUIRED(40003, "系统只支持只读数据查询，不支持数据修改操作"),
    SQL_EXECUTION_FAILED(50002, "数据查询执行失败，请稍后再试"),
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
