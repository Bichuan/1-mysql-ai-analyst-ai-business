package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationMessage;
import com.aianalyst.entity.ConversationSession;
import com.aianalyst.mapper.ConversationMessageMapper;
import com.aianalyst.mapper.ConversationSessionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transactional MySQL source of truth for conversation sessions and messages. */
@Service
public class ConversationPersistenceService {

    private static final int MAX_TITLE_LENGTH = 120;

    private final ConversationSessionMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;

    public ConversationPersistenceService(ConversationSessionMapper sessionMapper,
                                          ConversationMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public ConversationSession findOrCreateOwnedSession(Long userId,
                                                        String conversationId,
                                                        String firstQuestion) {
        ConversationSession existing = sessionMapper.selectByConversationId(conversationId);
        if (existing != null) {
            verifyOwner(existing, userId);
            touch(existing);
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationSession created = new ConversationSession();
        created.setConversationId(conversationId);
        created.setUserId(userId);
        created.setTitle(truncate(firstQuestion, MAX_TITLE_LENGTH));
        created.setSummaryUntilTurn(0L);
        created.setCurrentTurn(0L);
        created.setEstimatedTokens(0);
        created.setVersion(0L);
        created.setStatus("ACTIVE");
        created.setLastActiveAt(now);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        try {
            sessionMapper.insert(created);
            return created;
        } catch (DuplicateKeyException exception) {
            // A duplicated UUID is extremely unlikely, but concurrent first requests must still
            // verify ownership rather than silently attaching to another user's conversation.
            ConversationSession raced = sessionMapper.selectByConversationId(conversationId);
            if (raced == null) {
                throw exception;
            }
            verifyOwner(raced, userId);
            return raced;
        }
    }

    public ConversationSession findOwnedSession(Long userId, String conversationId) {
        ConversationSession session = sessionMapper.selectByConversationId(conversationId);
        if (session == null) {
            return null;
        }
        verifyOwner(session, userId);
        return session;
    }

    @Transactional
    public PersistedTurn appendTurn(Long userId,
                                    String conversationId,
                                    String originalQuestion,
                                    String standaloneQuestion,
                                    String answerSummary,
                                    Long queryHistoryId,
                                    String status,
                                    int userEstimatedTokens,
                                    int assistantEstimatedTokens) {
        ConversationSession session = sessionMapper.selectOwnedForUpdate(userId, conversationId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已失效");
        }

        long turnId = safeLong(session.getCurrentTurn()) + 1;
        long version = safeLong(session.getVersion()) + 1;
        LocalDateTime now = LocalDateTime.now();

        session.setCurrentTurn(turnId);
        session.setVersion(version);
        int contextEstimatedTokens = safeInteger(session.getEstimatedTokens());
        if ("SUCCESS".equals(status)) {
            contextEstimatedTokens = saturatedAdd(
                    contextEstimatedTokens, userEstimatedTokens, assistantEstimatedTokens);
        }
        session.setEstimatedTokens(contextEstimatedTokens);
        session.setLastActiveAt(now);
        session.setUpdatedAt(now);
        sessionMapper.updateById(session);

        ConversationMessage userMessage = new ConversationMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setTurnId(turnId);
        userMessage.setRole("USER");
        userMessage.setOriginalContent(originalQuestion);
        userMessage.setStandaloneQuestion(standaloneQuestion);
        userMessage.setStatus(status);
        userMessage.setEstimatedTokens(userEstimatedTokens);
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        ConversationMessage assistantMessage = new ConversationMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setTurnId(turnId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setAnswerSummary(answerSummary);
        assistantMessage.setQueryHistoryId(queryHistoryId);
        assistantMessage.setStatus(status);
        assistantMessage.setEstimatedTokens(assistantEstimatedTokens);
        assistantMessage.setCreatedAt(now);
        messageMapper.insert(assistantMessage);

        ConversationTurnSnapshot turn = new ConversationTurnSnapshot(
                turnId, originalQuestion, standaloneQuestion, answerSummary,
                queryHistoryId, status, now);
        return new PersistedTurn(
                turn, version, assistantMessage.getId(), contextEstimatedTokens);
    }

    public List<ConversationTurnSnapshot> loadRecentSuccessfulTurns(Long sessionId, int turnCount) {
        return loadRecentSuccessfulTurns(sessionId, 0L, turnCount);
    }

    public List<ConversationTurnSnapshot> loadRecentSuccessfulTurns(Long sessionId,
                                                                    long afterTurnExclusive,
                                                                    int turnCount) {
        int messageLimit = turnCount * 2;
        List<ConversationMessage> messages = messageMapper.selectList(
                Wrappers.<ConversationMessage>lambdaQuery()
                        .eq(ConversationMessage::getSessionId, sessionId)
                        .eq(ConversationMessage::getStatus, "SUCCESS")
                        .gt(ConversationMessage::getTurnId, afterTurnExclusive)
                        .orderByDesc(ConversationMessage::getTurnId)
                        .orderByDesc(ConversationMessage::getId)
                        .last("LIMIT " + messageLimit));

        Map<Long, TurnParts> grouped = new LinkedHashMap<>();
        for (ConversationMessage message : messages) {
            TurnParts parts = grouped.computeIfAbsent(message.getTurnId(), ignored -> new TurnParts());
            if ("USER".equals(message.getRole())) {
                parts.originalQuestion = message.getOriginalContent();
                parts.standaloneQuestion = message.getStandaloneQuestion();
                parts.createdAt = message.getCreatedAt();
            } else if ("ASSISTANT".equals(message.getRole())) {
                parts.answerSummary = message.getAnswerSummary();
                parts.queryHistoryId = message.getQueryHistoryId();
                if (parts.createdAt == null) {
                    parts.createdAt = message.getCreatedAt();
                }
            }
        }

        return grouped.entrySet().stream()
                .filter(entry -> entry.getValue().originalQuestion != null)
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getValue().toSnapshot(entry.getKey()))
                .toList();
    }

    public boolean compareAndSetContextState(Long userId,
                                             String conversationId,
                                             long expectedVersion,
                                             String rollingSummary,
                                             long summaryUntilTurn,
                                             String structuredState,
                                             int estimatedTokens) {
        return sessionMapper.compareAndSetContextState(
                userId, conversationId, expectedVersion, rollingSummary,
                summaryUntilTurn, structuredState, estimatedTokens) == 1;
    }

    public void linkQueryHistory(Long assistantMessageId, Long queryHistoryId) {
        if (assistantMessageId == null || queryHistoryId == null) {
            return;
        }
        messageMapper.linkQueryHistory(assistantMessageId, queryHistoryId);
    }

    private void touch(ConversationSession session) {
        LocalDateTime now = LocalDateTime.now();
        session.setLastActiveAt(now);
        session.setUpdatedAt(now);
        sessionMapper.updateById(session);
    }

    private void verifyOwner(ConversationSession session, Long userId) {
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private int saturatedAdd(int first, int second, int third) {
        return (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, first) + Math.max(0, second) + Math.max(0, third));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static final class TurnParts {
        private String originalQuestion;
        private String standaloneQuestion;
        private String answerSummary;
        private Long queryHistoryId;
        private LocalDateTime createdAt;

        private ConversationTurnSnapshot toSnapshot(long turnId) {
            return new ConversationTurnSnapshot(turnId, originalQuestion, standaloneQuestion,
                    answerSummary, queryHistoryId, "SUCCESS", createdAt);
        }
    }

    /** The committed turn plus the session version produced by the same row-locked transaction. */
    public record PersistedTurn(ConversationTurnSnapshot turn,
                                long version,
                                Long assistantMessageId,
                                int estimatedTokens) {
    }
}
