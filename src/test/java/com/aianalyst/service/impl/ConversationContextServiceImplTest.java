package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationContextUpdateCommand;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationSession;
import com.aianalyst.service.TokenEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceImplTest {

    private static final String CONVERSATION_ID = "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a";

    @Mock
    private ConversationPersistenceService persistenceService;

    @Mock
    private RedisConversationContextStore redisStore;

    @Mock
    private TokenEstimator tokenEstimator;

    private final ConversationProperties properties = new ConversationProperties();

    @Test
    void shouldUseRedisWithoutReadingMysqlWhenActiveContextExists() {
        ConversationContextServiceImpl service = service();
        ConversationContextSnapshot cached = new ConversationContextSnapshot(
                CONVERSATION_ID, null, 0, null, 1, 1, 0, List.of());
        when(redisStore.get(7L, CONVERSATION_ID)).thenReturn(Optional.of(cached));

        String resolved = service.openSession(7L, CONVERSATION_ID, "查询客户");

        assertThat(resolved).isEqualTo(CONVERSATION_ID);
        verify(redisStore).touch(7L, CONVERSATION_ID);
        verify(persistenceService, never()).findOrCreateOwnedSession(7L, CONVERSATION_ID, "查询客户");
    }

    @Test
    void shouldRestoreRedisFromMysqlOnCacheMiss() {
        ConversationContextServiceImpl service = service();
        ConversationSession session = session();
        ConversationTurnSnapshot turn = turn();
        when(redisStore.get(7L, CONVERSATION_ID)).thenReturn(Optional.empty());
        when(persistenceService.findOrCreateOwnedSession(7L, CONVERSATION_ID, "查询客户"))
                .thenReturn(session);
        when(persistenceService.loadRecentSuccessfulTurns(11L, 0L, 3)).thenReturn(List.of(turn));

        assertThat(service.openSession(7L, CONVERSATION_ID, "查询客户"))
                .isEqualTo(CONVERSATION_ID);

        verify(redisStore).restore(session, List.of(turn));
    }

    @Test
    void shouldPersistBeforeHistoryCompletesAndThenLinkHistoryId() {
        ConversationContextServiceImpl service = service();
        ConversationTurnSnapshot turn = turn();
        when(persistenceService.appendTurn(
                7L, CONVERSATION_ID, "那华东呢？", "查询华东销售额",
                "华东销售额为100万元", null, "SUCCESS", 6, 4))
                .thenReturn(new ConversationPersistenceService.PersistedTurn(turn, 6L, 21L, 140));

        CompletableFuture<Long> historyIdFuture = new CompletableFuture<>();

        service.recordTurn(
                7L, CONVERSATION_ID, "那华东呢？", "查询华东销售额",
                "华东销售额为100万元", "SUCCESS", historyIdFuture);

        verify(redisStore).appendSuccessfulTurn(7L, CONVERSATION_ID, turn, 6L, 140);
        verify(persistenceService, never()).linkQueryHistory(21L, 99L);

        historyIdFuture.complete(99L);

        verify(persistenceService).linkQueryHistory(21L, 99L);
    }

    @Test
    void shouldCommitContextStateToMysqlBeforeUpdatingRedis() {
        ConversationContextServiceImpl service = service();
        ConversationContextUpdateCommand command = new ConversationContextUpdateCommand(
                7L, "滚动摘要", 2L, "{\"metric\":\"销售额\"}", 130, 1, false);
        when(persistenceService.compareAndSetContextState(
                7L, CONVERSATION_ID, 7L, "滚动摘要", 2L,
                "{\"metric\":\"销售额\"}", 130))
                .thenReturn(true);
        when(redisStore.updateContext(
                7L, CONVERSATION_ID, 7L, 8L, "滚动摘要", 2L,
                "{\"metric\":\"销售额\"}", 130, 1, false))
                .thenReturn(false);

        assertThat(service.updateContext(7L, CONVERSATION_ID, command)).isTrue();

        verify(redisStore).evict(7L, CONVERSATION_ID);
    }

    @Test
    void shouldRejectMalformedConversationIdBeforeStorageAccess() {
        ConversationContextServiceImpl service = service();

        assertThatThrownBy(() -> service.openSession(7L, "not-a-uuid", "查询客户"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("conversationId 格式不正确");
    }

    private ConversationContextServiceImpl service() {
        return new ConversationContextServiceImpl(
                persistenceService, redisStore, properties, tokenEstimator);
    }

    private ConversationSession session() {
        ConversationSession session = new ConversationSession();
        session.setId(11L);
        session.setUserId(7L);
        session.setConversationId(CONVERSATION_ID);
        session.setCurrentTurn(1L);
        session.setVersion(1L);
        return session;
    }

    private ConversationTurnSnapshot turn() {
        return new ConversationTurnSnapshot(
                3, "那华东呢？", "查询华东销售额", "华东销售额为100万元",
                99L, "SUCCESS", LocalDateTime.of(2026, 7, 19, 10, 0));
    }
}
