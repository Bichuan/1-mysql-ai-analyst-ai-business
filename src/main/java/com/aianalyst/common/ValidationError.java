package com.aianalyst.common;

/**
 * One field-level validation failure returned to the client.
 */
public record ValidationError(String field, String message) {
}
