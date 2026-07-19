package com.aianalyst.dto;

import java.util.List;

/** Active conversation state reconstructed from Redis or the system database. */
public record ConversationContextSnapshot(
        String conversationId,
        String rollingSummary,
        long summaryUntilTurn,
        String structuredState,
        long currentTurn,
        long version,
        List<ConversationTurnSnapshot> recentTurns) {

    public ConversationContextSnapshot {
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }
}
