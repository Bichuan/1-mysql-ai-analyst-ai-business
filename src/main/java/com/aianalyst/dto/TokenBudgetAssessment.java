package com.aianalyst.dto;

/** Conservative estimate for one model request, including reserved output and safety margin. */
public record TokenBudgetAssessment(
        int estimatedPromptTokens,
        int reservedOutputTokens,
        int safetyMarginTokens,
        int thresholdTokens,
        int modelContextWindowTokens,
        boolean exceedsLimit) {

    public int estimatedTotalTokens() {
        return estimatedPromptTokens + reservedOutputTokens + safetyMarginTokens;
    }

    public int maxPromptTokens() {
        return Math.max(0, thresholdTokens - reservedOutputTokens - safetyMarginTokens);
    }

    public double estimatedWindowUsageRatio() {
        return modelContextWindowTokens == 0
                ? 1.0D
                : (double) estimatedTotalTokens() / modelContextWindowTokens;
    }
}
