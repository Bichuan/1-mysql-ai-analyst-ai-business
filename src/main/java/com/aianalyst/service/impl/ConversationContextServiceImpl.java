package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationContextUpdateCommand;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationSession;
import com.aianalyst.service.ConversationContextService;
import com.aianalyst.service.TokenEstimator;
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
    private final TokenEstimator tokenEstimator;

    public ConversationContextServiceImpl(ConversationPersistenceService persistenceService,
                                          RedisConversationContextStore redisStore,
                                          ConversationProperties properties,
                                          TokenEstimator tokenEstimator) {
        this.persistenceService = persistenceService;
        this.redisStore = redisStore;
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
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
                session.getId(), safeLong(session.getSummaryUntilTurn()), properties.getRecentTurnCount());
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
                session.getId(), safeLong(session.getSummaryUntilTurn()), properties.getRecentTurnCount());
        redisStore.restore(session, recentTurns);
        return redisStore.get(userId, conversationId)
                .or(() -> Optional.of(toSnapshot(session, recentTurns)));
    }

    @Override
    public boolean updateContext(Long userId,
                                 String conversationId,
                                 ConversationContextUpdateCommand command) {
        boolean mysqlUpdated = persistenceService.compareAndSetContextState(
                userId,
                conversationId,
                command.expectedVersion(),
                command.rollingSummary(),
                command.summaryUntilTurn(),
                command.structuredState(),
                command.estimatedTokens());
        if (!mysqlUpdated) {
            return false;
        }

        long newVersion = command.expectedVersion() + 1;
        boolean redisUpdated = redisStore.updateContext(
                userId,
                conversationId,
                command.expectedVersion(),
                newVersion,
                command.rollingSummary(),
                command.summaryUntilTurn(),
                command.structuredState(),
                command.estimatedTokens(),
                command.removeOldestTurns(),
                command.clearRecentTurns());
        if (!redisUpdated) {
            // MySQL has committed the authoritative state. Remove a stale hot copy so the next
            // request reconstructs both keys instead of reading mixed versions.
            redisStore.evict(userId, conversationId);
        }
        return true;
    }

    @Override
    public void recordTurn(Long userId,
                           String conversationId,
                           String originalQuestion,
                           String standaloneQuestion,
                           String answerSummary,
                           String status,
                           CompletableFuture<Long> queryHistoryIdFuture) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        try {
            int userEstimatedTokens = saturatedAdd(
                    tokenEstimator.estimate(originalQuestion),
                    tokenEstimator.estimate(standaloneQuestion),
                    6);
            int assistantEstimatedTokens = saturatedAdd(
                    tokenEstimator.estimate(answerSummary), 4, 0);
            ConversationPersistenceService.PersistedTurn persistedTurn = persistenceService.appendTurn(
                    userId, conversationId, originalQuestion, standaloneQuestion,
                    answerSummary, null, status,
                    userEstimatedTokens, assistantEstimatedTokens);
            if ("SUCCESS".equals(status)) {
                redisStore.appendSuccessfulTurn(
                        userId, conversationId, persistedTurn.turn(), persistedTurn.version(),
                        persistedTurn.estimatedTokens());
            } else {
                // Failed and rejected turns remain durable audit data but must never become reusable
                // LLM memory. Eviction also prevents a stale Redis version after the MySQL increment.
                redisStore.evict(userId, conversationId);
            }

            CompletableFuture<Long> historyFuture = queryHistoryIdFuture == null
                    ? CompletableFuture.completedFuture(null)
                    : queryHistoryIdFuture;
            historyFuture.whenComplete((queryHistoryId, historyFailure) -> {
                if (historyFailure != null) {
                    log.warn("Query history id was unavailable for conversation turn. conversationId={}, cause={}",
                            conversationId, historyFailure.getClass().getSimpleName());
                    return;
                }
                try {
                    persistenceService.linkQueryHistory(
                            persistedTurn.assistantMessageId(), queryHistoryId);
                } catch (RuntimeException exception) {
                    log.warn("Failed to link conversation message to query history. conversationId={}, cause={}",
                            conversationId, exception.getClass().getSimpleName());
                }
            });
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
                session.getEstimatedTokens() == null ? 0 : Math.max(0, session.getEstimatedTokens()),
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

    private int saturatedAdd(int first, int second, int third) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, first) + Math.max(0, second) + Math.max(0, third));
    }
}
