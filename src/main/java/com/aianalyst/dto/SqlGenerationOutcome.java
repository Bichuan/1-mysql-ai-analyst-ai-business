package com.aianalyst.dto;

import com.aianalyst.vo.SqlGenerationVO;

/** Audited SQL plus the shared correction budget already consumed during generation. */
public record SqlGenerationOutcome(
        SqlGenerationVO result,
        int correctionAttemptsUsed) {

    public SqlGenerationOutcome {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (correctionAttemptsUsed < 0) {
            throw new IllegalArgumentException("correctionAttemptsUsed must not be negative");
        }
    }

    public static SqlGenerationOutcome initial(SqlGenerationVO result) {
        return new SqlGenerationOutcome(result, 0);
    }
}
