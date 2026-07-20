package com.aianalyst.service.impl;

import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisConversationContextStoreTest {

    private static final String CONVERSATION_ID = "7bc58b98-9b9d-4f6f-9fa5-429d94f2ee4a";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Test
    void shouldLoadMetadataAndRecentTurnsFromSeparateRedisStructures() {
        RedisConversationContextStore store = store();
        String metaKey = RedisConversationContextStore.metaKey(7L, CONVERSATION_ID);
        String turnsKey = RedisConversationContextStore.turnsKey(7L, CONVERSATION_ID);
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(hashOperations.entries(metaKey)).thenReturn(Map.of(
                "userId", "7",
                "conversationId", CONVERSATION_ID,
                "rollingSummary", "历史摘要",
                "summaryUntilTurn", "4",
                "structuredState", "{}",
                "estimatedTokens", "320",
                "currentTurn", "7",
                "version", "9"));
        when(listOperations.range(turnsKey, 0, -1)).thenReturn(java.util.List.of());

        Optional<ConversationContextSnapshot> result = store.get(7L, CONVERSATION_ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().rollingSummary()).isEqualTo("历史摘要");
        assertThat(result.orElseThrow().summaryUntilTurn()).isEqualTo(4L);
        assertThat(result.orElseThrow().currentTurn()).isEqualTo(7L);
        assertThat(result.orElseThrow().estimatedTokens()).isEqualTo(320);
    }

    @Test
    void shouldPlaceMetaAndTurnsInTheSameRedisClusterHashSlot() {
        String metaKey = RedisConversationContextStore.metaKey(7L, CONVERSATION_ID);
        String turnsKey = RedisConversationContextStore.turnsKey(7L, CONVERSATION_ID);

        assertThat(metaKey).isEqualTo(
                "conversation:v1:{7:" + CONVERSATION_ID + "}:meta");
        assertThat(turnsKey).isEqualTo(
                "conversation:v1:{7:" + CONVERSATION_ID + "}:turns");
        assertThat(metaKey).contains("{7:" + CONVERSATION_ID + "}");
        assertThat(turnsKey).contains("{7:" + CONVERSATION_ID + "}");
    }

    @Test
    void shouldEvictHotCopyWhenAnAppendArrivesOutOfVersionOrder() {
        RedisConversationContextStore store = store();
        ConversationTurnSnapshot turn = new ConversationTurnSnapshot(
                4L, "那华东呢？", "查询华东销售额", "华东销售额为100万元",
                null, "SUCCESS", LocalDateTime.of(2026, 7, 20, 9, 0));
        doReturn(0L).when(redisTemplate).execute(any(), anyList(), any(Object[].class));

        store.appendSuccessfulTurn(7L, CONVERSATION_ID, turn, 8L, 240);

        verify(redisTemplate).delete(List.of(
                RedisConversationContextStore.metaKey(7L, CONVERSATION_ID),
                RedisConversationContextStore.turnsKey(7L, CONVERSATION_ID)));
    }

    private RedisConversationContextStore store() {
        return new RedisConversationContextStore(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), new ConversationProperties());
    }
}
