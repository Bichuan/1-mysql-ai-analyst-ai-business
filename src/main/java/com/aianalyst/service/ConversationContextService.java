package com.aianalyst.service;

import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationContextUpdateCommand;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Creates, restores and records user-owned conversation context. */
public interface ConversationContextService {

    /** Resolves an existing owned session or creates a new one when no id was supplied. */
    String openSession(Long userId, String requestedConversationId, String firstQuestion);

    /** Loads the active state from Redis, falling back to MySQL and warming Redis again. */
    Optional<ConversationContextSnapshot> loadContext(Long userId, String conversationId);

    /** Applies model-derived state only when the stored version still matches the snapshot. */
    boolean updateContext(Long userId,
                          String conversationId,
                          ConversationContextUpdateCommand command);

    /**
     * Persists the turn before the HTTP response returns, then asynchronously backfills the
     * query-history id when the independent audit write completes.
     */
    void recordTurn(Long userId,
                    String conversationId,
                    String originalQuestion,
                    String standaloneQuestion,
                    String answerSummary,
                    String status,
                    CompletableFuture<Long> queryHistoryIdFuture);
}
