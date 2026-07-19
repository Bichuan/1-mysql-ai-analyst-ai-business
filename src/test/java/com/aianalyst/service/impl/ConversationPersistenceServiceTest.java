package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationMessage;
import com.aianalyst.entity.ConversationSession;
import com.aianalyst.mapper.ConversationMessageMapper;
import com.aianalyst.mapper.ConversationSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationPersistenceServiceTest {

    @Mock
    private ConversationSessionMapper sessionMapper;

    @Mock
    private ConversationMessageMapper messageMapper;

    @Test
    void shouldCreateAUserOwnedSessionWithInitialState() {
        ConversationPersistenceService service = new ConversationPersistenceService(sessionMapper, messageMapper);
        String conversationId = "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a";

        ConversationSession session = service.findOrCreateOwnedSession(
                7L, conversationId, "查询2025年各地区销售额");

        assertThat(session.getConversationId()).isEqualTo(conversationId);
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getCurrentTurn()).isZero();
        assertThat(session.getVersion()).isZero();
        assertThat(session.getStatus()).isEqualTo("ACTIVE");
        verify(sessionMapper).insert(session);
    }

    @Test
    void shouldRejectAConversationOwnedByAnotherUser() {
        ConversationPersistenceService service = new ConversationPersistenceService(sessionMapper, messageMapper);
        ConversationSession existing = session(11L, 8L, 0L, 0L);
        when(sessionMapper.selectByConversationId(existing.getConversationId())).thenReturn(existing);

        assertThatThrownBy(() -> service.findOrCreateOwnedSession(
                7L, existing.getConversationId(), "查询销售额"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该会话");

        verify(sessionMapper, never()).insert(any(ConversationSession.class));
    }

    @Test
    void shouldPersistUserAndAssistantMessagesInOneTurn() {
        ConversationPersistenceService service = new ConversationPersistenceService(sessionMapper, messageMapper);
        ConversationSession session = session(11L, 7L, 2L, 4L);
        when(sessionMapper.selectOwnedForUpdate(7L, session.getConversationId())).thenReturn(session);

        ConversationPersistenceService.PersistedTurn persisted = service.appendTurn(
                7L, session.getConversationId(), "那华东呢？", "查询华东销售额",
                "华东销售额为100万元", 99L, "SUCCESS");

        assertThat(persisted.turn().turnId()).isEqualTo(3L);
        assertThat(persisted.version()).isEqualTo(5L);
        verify(sessionMapper).updateById(session);

        ArgumentCaptor<ConversationMessage> messageCaptor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        List<ConversationMessage> messages = messageCaptor.getAllValues();
        assertThat(messages).extracting(ConversationMessage::getRole)
                .containsExactly("USER", "ASSISTANT");
        assertThat(messages.get(0).getOriginalContent()).isEqualTo("那华东呢？");
        assertThat(messages.get(0).getStandaloneQuestion()).isEqualTo("查询华东销售额");
        assertThat(messages.get(1).getAnswerSummary()).isEqualTo("华东销售额为100万元");
        assertThat(messages.get(1).getQueryHistoryId()).isEqualTo(99L);
    }

    private ConversationSession session(Long id, Long userId, Long currentTurn, Long version) {
        ConversationSession session = new ConversationSession();
        session.setId(id);
        session.setConversationId("7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a");
        session.setUserId(userId);
        session.setCurrentTurn(currentTurn);
        session.setVersion(version);
        return session;
    }
}
