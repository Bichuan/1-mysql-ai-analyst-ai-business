package com.aianalyst.service;

import com.aianalyst.dto.ConversationContextSnapshot;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Creates, restores and records user-owned conversation context. */
public interface ConversationContextService {

    /** Resolves an existing owned session or creates a new one when no id was supplied. */
    String openSession(Long userId, String requestedConversationId, String firstQuestion);

    /** Loads the active state from Redis, falling back to MySQL and warming Redis again. */
    Optional<ConversationContextSnapshot> loadContext(Long userId, String conversationId);

    /** Records a turn after its asynchronous query-history row has obtained an id. */
    void recordTurnAfterHistory(Long userId,
                                String conversationId,
                                String originalQuestion,
                                String standaloneQuestion,
                                String answerSummary,
                                String status,
                                CompletableFuture<Long> queryHistoryIdFuture);
}
