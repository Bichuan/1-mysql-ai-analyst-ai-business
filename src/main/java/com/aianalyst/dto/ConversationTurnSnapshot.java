package com.aianalyst.dto;

import java.time.LocalDateTime;

/** A compact, masked conversation turn safe to keep in the active Redis context. */
public record ConversationTurnSnapshot(
        long turnId,
        String originalQuestion,
        String standaloneQuestion,
        String answerSummary,
        Long queryHistoryId,
        String status,
        LocalDateTime createdAt) {
}
