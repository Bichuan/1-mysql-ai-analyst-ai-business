package com.aianalyst.service;

/** Validates and normalizes model-generated SQL before it can enter the execution phase. */
public interface SqlAuditService {

    String auditAndNormalize(String sql);
}
