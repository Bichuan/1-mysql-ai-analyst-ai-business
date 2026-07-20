package com.aianalyst.service;

import com.aianalyst.dto.ConversationTurnSnapshot;

import java.util.List;

/** Conservative local estimator used when the remote model tokenizer is unavailable. */
public interface TokenEstimator {

    int estimate(String text);

    int estimateConversationContext(String rollingSummary,
                                    String structuredState,
                                    List<ConversationTurnSnapshot> recentTurns);

    int estimateTurn(String originalQuestion, String standaloneQuestion, String answerSummary);
}
