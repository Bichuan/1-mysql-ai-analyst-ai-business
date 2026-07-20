package com.aianalyst.service;

import com.aianalyst.dto.SqlGenerationOutcome;
import com.aianalyst.vo.SqlGenerationVO;

/** Generates SQL from a natural-language business question without executing it. */
public interface TextToSqlService {

    SqlGenerationVO generateSql(String question);

    /**
     * Generates and audits SQL, allowing one bounded retry only for malformed or multi-statement
     * model output. The returned counter is shared with later database-execution correction.
     */
    SqlGenerationOutcome generateSqlWithAuditRecovery(String question);

    /**
     * Repairs a SQL statement after a syntax-level database failure. The returned SQL has
     * already passed the same audit used for a first-pass generated statement.
     */
    String correctSql(String question, String failedSql, String databaseError);
}
