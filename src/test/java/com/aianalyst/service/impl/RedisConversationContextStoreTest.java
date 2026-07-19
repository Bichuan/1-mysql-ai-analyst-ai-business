package com.aianalyst.service.impl;

import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
                "currentTurn", "7",
                "version", "9"));
        when(listOperations.range(turnsKey, -3, -1)).thenReturn(java.util.List.of());

        Optional<ConversationContextSnapshot> result = store.get(7L, CONVERSATION_ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().rollingSummary()).isEqualTo("历史摘要");
        assertThat(result.orElseThrow().summaryUntilTurn()).isEqualTo(4L);
        assertThat(result.orElseThrow().currentTurn()).isEqualTo(7L);
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

    private RedisConversationContextStore store() {
        return new RedisConversationContextStore(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), new ConversationProperties());
    }
}
