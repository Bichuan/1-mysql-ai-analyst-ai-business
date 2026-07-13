package com.aianalyst.service;

import java.util.List;
import java.util.Map;

/** Executes a previously audited SELECT statement against the read-only business datasource. */
public interface SqlExecutionService {

    List<Map<String, Object>> executeAuditedSelect(String auditedSql);
}
