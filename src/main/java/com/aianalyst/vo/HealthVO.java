package com.aianalyst.vo;

/**
 * Minimal application availability response used during local development and deployment checks.
 */
public record HealthVO(String status, String application) {
}
