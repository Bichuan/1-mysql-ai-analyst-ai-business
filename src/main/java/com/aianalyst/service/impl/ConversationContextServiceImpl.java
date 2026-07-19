package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationSession;
import com.aianalyst.service.ConversationContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Coordinates the request-local, Redis and MySQL conversation storage layers. */
@Service
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextServiceImpl.class);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ConversationPersistenceService persistenceService;
    private final RedisConversationContextStore redisStore;
    private final ConversationProperties properties;

    public ConversationContextServiceImpl(ConversationPersistenceService persistenceService,
                                          RedisConversationContextStore redisStore,
                                          ConversationProperties properties) {
        this.persistenceService = persistenceService;
        this.redisStore = redisStore;
        this.properties = properties;
    }

    @Override
    public String openSession(Long userId, String requestedConversationId, String firstQuestion) {
        String conversationId = resolveConversationId(requestedConversationId);
        Optional<ConversationContextSnapshot> cached = redisStore.get(userId, conversationId);
        if (cached.isPresent()) {
            redisStore.touch(userId, conversationId);
            return conversationId;
        }

        ConversationSession session = persistenceService.findOrCreateOwnedSession(
                userId, conversationId, firstQuestion);
        List<ConversationTurnSnapshot> recentTurns = persistenceService.loadRecentSuccessfulTurns(
                session.getId(), properties.getRecentTurnCount());
        redisStore.restore(session, recentTurns);
        return conversationId;
    }

    @Override
    public Optional<ConversationContextSnapshot> loadContext(Long userId, String conversationId) {
        validateConversationId(conversationId);
        Optional<ConversationContextSnapshot> cached = redisStore.get(userId, conversationId);
        if (cached.isPresent()) {
            redisStore.touch(userId, conversationId);
            return cached;
        }

        ConversationSession session = persistenceService.findOwnedSession(userId, conversationId);
        if (session == null) {
            return Optional.empty();
        }
        List<ConversationTurnSnapshot> recentTurns = persistenceService.loadRecentSuccessfulTurns(
                session.getId(), properties.getRecentTurnCount());
        redisStore.restore(session, recentTurns);
        return redisStore.get(userId, conversationId)
                .or(() -> Optional.of(toSnapshot(session, recentTurns)));
    }

    @Override
    public void recordTurnAfterHistory(Long userId,
                                       String conversationId,
                                       String originalQuestion,
                                       String standaloneQuestion,
                                       String answerSummary,
                                       String status,
                                       CompletableFuture<Long> queryHistoryIdFuture) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        CompletableFuture<Long> historyFuture = queryHistoryIdFuture == null
                ? CompletableFuture.completedFuture(null)
                : queryHistoryIdFuture;
        historyFuture.whenComplete((queryHistoryId, historyFailure) -> {
            if (historyFailure != null) {
                log.warn("Query history id was unavailable for conversation turn. conversationId={}, cause={}",
                        conversationId, historyFailure.getClass().getSimpleName());
            }
            persistTurnSafely(userId, conversationId, originalQuestion, standaloneQuestion,
                    answerSummary, queryHistoryId, status);
        });
    }

    private void persistTurnSafely(Long userId,
                                   String conversationId,
                                   String originalQuestion,
                                   String standaloneQuestion,
                                   String answerSummary,
                                   Long queryHistoryId,
                                   String status) {
        try {
            ConversationPersistenceService.PersistedTurn persistedTurn = persistenceService.appendTurn(
                    userId, conversationId, originalQuestion, standaloneQuestion,
                    answerSummary, queryHistoryId, status);
            if ("SUCCESS".equals(status)) {
                redisStore.appendSuccessfulTurn(
                        userId, conversationId, persistedTurn.turn(), persistedTurn.version());
            }
        } catch (RuntimeException exception) {
            // The user's query result is already available. Context persistence is auxiliary and
            // must not turn a successful read-only query into a failed HTTP response.
            log.error("Failed to persist conversation turn. conversationId={}, status={}",
                    conversationId, status, exception);
        }
    }

    private ConversationContextSnapshot toSnapshot(ConversationSession session,
                                                    List<ConversationTurnSnapshot> recentTurns) {
        return new ConversationContextSnapshot(
                session.getConversationId(),
                session.getRollingSummary(),
                safeLong(session.getSummaryUntilTurn()),
                session.getStructuredState(),
                safeLong(session.getCurrentTurn()),
                safeLong(session.getVersion()),
                recentTurns);
    }

    private String resolveConversationId(String requestedConversationId) {
        if (!StringUtils.hasText(requestedConversationId)) {
            return UUID.randomUUID().toString();
        }
        validateConversationId(requestedConversationId);
        return requestedConversationId.toLowerCase();
    }

    private void validateConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId) || !UUID_PATTERN.matcher(conversationId).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "conversationId 格式不正确");
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
