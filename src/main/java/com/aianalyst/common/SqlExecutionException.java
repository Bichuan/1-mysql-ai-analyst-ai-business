package com.aianalyst.common;

/**
 * Indicates that an audited business SQL statement could not be executed.
 * The original cause is retained for server-side logging and the later
 * SQL self-correction workflow, but is never exposed through the API.
 */
public class SqlExecutionException extends RuntimeException {

    private final String sql;

    public SqlExecutionException(String sql, Throwable cause) {
        super("Business SQL execution failed", cause);
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }
}
