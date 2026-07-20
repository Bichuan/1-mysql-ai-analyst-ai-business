package com.aianalyst.dto;

/** Optimistic update produced by one context-planning pass. */
public record ConversationContextUpdateCommand(
        long expectedVersion,
        String rollingSummary,
        long summaryUntilTurn,
        String structuredState,
        int estimatedTokens,
        int removeOldestTurns,
        boolean clearRecentTurns) {

    public ConversationContextUpdateCommand {
        if (expectedVersion < 0 || summaryUntilTurn < 0
                || estimatedTokens < 0 || removeOldestTurns < 0) {
            throw new IllegalArgumentException("conversation context counters must not be negative");
        }
    }
}
