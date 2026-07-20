package com.aianalyst.service.impl;

import com.aianalyst.common.RedisKeyPrefix;
import com.aianalyst.config.ConversationProperties;
import com.aianalyst.dto.ConversationContextSnapshot;
import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.entity.ConversationSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Redis hot store: one Hash for session state and one List for recent compact turns. */
@Component
public class RedisConversationContextStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationContextStore.class);

    private static final DefaultRedisScript<Long> APPEND_TURN_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion or tostring(currentVersion) ~= tostring(ARGV[1]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'currentTurn', ARGV[3],
                'version', ARGV[2],
                'estimatedTokens', ARGV[4],
                'lastActiveTime', ARGV[5])
            redis.call('RPUSH', KEYS[2], ARGV[6])
            redis.call('EXPIRE', KEYS[1], ARGV[7])
            redis.call('EXPIRE', KEYS[2], ARGV[7])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> UPDATE_CONTEXT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion or tostring(currentVersion) ~= tostring(ARGV[1]) then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'rollingSummary', ARGV[3],
                'summaryUntilTurn', ARGV[4],
                'structuredState', ARGV[5],
                'estimatedTokens', ARGV[6],
                'version', ARGV[2],
                'lastActiveTime', ARGV[9])
            if ARGV[8] == '1' then
                redis.call('DEL', KEYS[2])
            elseif tonumber(ARGV[7]) > 0 then
                redis.call('LTRIM', KEYS[2], tonumber(ARGV[7]), -1)
            end
            redis.call('EXPIRE', KEYS[1], ARGV[10])
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[10])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RESTORE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[10])
                if redis.call('EXISTS', KEYS[2]) == 1 then
                    redis.call('EXPIRE', KEYS[2], ARGV[10])
                end
                return 0
            end
            redis.call('HSET', KEYS[1],
                'userId', ARGV[1],
                'conversationId', ARGV[2],
                'rollingSummary', ARGV[3],
                'summaryUntilTurn', ARGV[4],
                'structuredState', ARGV[5],
                'estimatedTokens', ARGV[6],
                'currentTurn', ARGV[7],
                'version', ARGV[8],
                'lastActiveTime', ARGV[9])
            redis.call('DEL', KEYS[2])
            for index = 11, #ARGV do
                redis.call('RPUSH', KEYS[2], ARGV[index])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[10])
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[10])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationProperties properties;

    public RedisConversationContextStore(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         ConversationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<ConversationContextSnapshot> get(Long userId, String conversationId) {
        String metaKey = metaKey(userId, conversationId);
        try {
            Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metaKey);
            if (metadata == null || metadata.isEmpty()) {
                return Optional.empty();
            }
            if (!String.valueOf(userId).equals(stringValue(metadata.get("userId")))) {
                log.warn("Conversation cache ownership metadata is invalid. key={}", metaKey);
                evict(userId, conversationId);
                return Optional.empty();
            }

            List<ConversationTurnSnapshot> turns = readTurns(userId, conversationId);
            return Optional.of(new ConversationContextSnapshot(
                    conversationId,
                    nullIfBlank(stringValue(metadata.get("rollingSummary"))),
                    longValue(metadata.get("summaryUntilTurn")),
                    nullIfBlank(stringValue(metadata.get("structuredState"))),
                    longValue(metadata.get("currentTurn")),
                    longValue(metadata.get("version")),
                    integerValue(metadata.get("estimatedTokens")),
                    turns));
        } catch (RuntimeException exception) {
            log.warn("Conversation cache read failed; MySQL fallback will be used. key={}, cause={}",
                    metaKey, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void restore(ConversationSession session, List<ConversationTurnSnapshot> recentTurns) {
        String metaKey = metaKey(session.getUserId(), session.getConversationId());
        String turnsKey = turnsKey(session.getUserId(), session.getConversationId());
        try {
            List<String> arguments = new ArrayList<>();
            arguments.add(String.valueOf(session.getUserId()));
            arguments.add(session.getConversationId());
            arguments.add(valueOrEmpty(session.getRollingSummary()));
            arguments.add(String.valueOf(safeLong(session.getSummaryUntilTurn())));
            arguments.add(valueOrEmpty(session.getStructuredState()));
            arguments.add(String.valueOf(safeInteger(session.getEstimatedTokens())));
            arguments.add(String.valueOf(safeLong(session.getCurrentTurn())));
            arguments.add(String.valueOf(safeLong(session.getVersion())));
            arguments.add(formatTime(session.getLastActiveAt()));
            arguments.add(String.valueOf(properties.getRedisTtl().toSeconds()));
            if (recentTurns != null && !recentTurns.isEmpty()) {
                arguments.addAll(recentTurns.stream().map(this::serialize).toList());
            }
            // Both keys share one Redis Cluster hash tag, so first-writer-wins restoration is atomic.
            redisTemplate.execute(
                    RESTORE_SCRIPT, List.of(metaKey, turnsKey), arguments.toArray());
        } catch (RuntimeException exception) {
            // Redis is an acceleration layer. MySQL data has already been committed at this point.
            log.warn("Conversation cache restore failed. key={}, cause={}",
                    metaKey, exception.getClass().getSimpleName());
        }
    }

    public void touch(Long userId, String conversationId) {
        String metaKey = metaKey(userId, conversationId);
        String turnsKey = turnsKey(userId, conversationId);
        try {
            redisTemplate.opsForHash().put(metaKey, "lastActiveTime", formatTime(LocalDateTime.now()));
            redisTemplate.expire(metaKey, properties.getRedisTtl());
            redisTemplate.expire(turnsKey, properties.getRedisTtl());
        } catch (RuntimeException exception) {
            log.warn("Conversation cache TTL refresh failed. key={}, cause={}",
                    metaKey, exception.getClass().getSimpleName());
        }
    }

    public void appendSuccessfulTurn(Long userId,
                                     String conversationId,
                                     ConversationTurnSnapshot turn,
                                     long version,
                                     int estimatedTokens) {
        String metaKey = metaKey(userId, conversationId);
        String turnsKey = turnsKey(userId, conversationId);
        try {
            Long updated = redisTemplate.execute(
                    APPEND_TURN_SCRIPT,
                    List.of(metaKey, turnsKey),
                    String.valueOf(version - 1),
                    String.valueOf(version),
                    String.valueOf(turn.turnId()),
                    String.valueOf(Math.max(0, estimatedTokens)),
                    formatTime(LocalDateTime.now()),
                    serialize(turn),
                    String.valueOf(properties.getRedisTtl().toSeconds()));
            if (!Long.valueOf(1L).equals(updated)) {
                // A concurrent request wrote a newer version first, or this hot copy missed an
                // intermediate update. Rebuild from MySQL instead of accepting an out-of-order list.
                evict(userId, conversationId);
            }
        } catch (RuntimeException exception) {
            log.warn("Conversation cache append failed; durable messages remain in MySQL. key={}, cause={}",
                    metaKey, exception.getClass().getSimpleName());
        }
    }

    public boolean updateContext(Long userId,
                                 String conversationId,
                                 long expectedVersion,
                                 long newVersion,
                                 String rollingSummary,
                                 long summaryUntilTurn,
                                 String structuredState,
                                 int estimatedTokens,
                                 int removeOldestTurns,
                                 boolean clearRecentTurns) {
        String metaKey = metaKey(userId, conversationId);
        String turnsKey = turnsKey(userId, conversationId);
        try {
            Long updated = redisTemplate.execute(
                    UPDATE_CONTEXT_SCRIPT,
                    List.of(metaKey, turnsKey),
                    String.valueOf(expectedVersion),
                    String.valueOf(newVersion),
                    valueOrEmpty(rollingSummary),
                    String.valueOf(summaryUntilTurn),
                    valueOrEmpty(structuredState),
                    String.valueOf(Math.max(0, estimatedTokens)),
                    String.valueOf(removeOldestTurns),
                    clearRecentTurns ? "1" : "0",
                    formatTime(LocalDateTime.now()),
                    String.valueOf(properties.getRedisTtl().toSeconds()));
            return Long.valueOf(1L).equals(updated);
        } catch (RuntimeException exception) {
            log.warn("Conversation cache state update failed. key={}, cause={}",
                    metaKey, exception.getClass().getSimpleName());
            return false;
        }
    }

    public void evict(Long userId, String conversationId) {
        try {
            redisTemplate.delete(List.of(
                    metaKey(userId, conversationId), turnsKey(userId, conversationId)));
        } catch (RuntimeException exception) {
            log.warn("Conversation cache eviction failed. conversationId={}, cause={}",
                    conversationId, exception.getClass().getSimpleName());
        }
    }

    public static String metaKey(Long userId, String conversationId) {
        return RedisKeyPrefix.CONVERSATION + hashTag(userId, conversationId) + ":meta";
    }

    public static String turnsKey(Long userId, String conversationId) {
        return RedisKeyPrefix.CONVERSATION + hashTag(userId, conversationId) + ":turns";
    }

    private static String hashTag(Long userId, String conversationId) {
        // The shared Redis Cluster hash tag keeps meta and turns on the same node for Lua updates.
        return "{" + userId + ':' + conversationId + '}';
    }

    private List<ConversationTurnSnapshot> readTurns(Long userId, String conversationId) {
        // A fourth item may exist briefly until its predecessor is safely merged into the summary.
        List<String> values = redisTemplate.opsForList().range(
                turnsKey(userId, conversationId), 0, -1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<ConversationTurnSnapshot> turns = new ArrayList<>(values.size());
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                turns.add(objectMapper.readValue(value, ConversationTurnSnapshot.class));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("invalid cached conversation turn", exception);
            }
        }
        return turns;
    }

    private String serialize(ConversationTurnSnapshot turn) {
        try {
            return objectMapper.writeValueAsString(turn);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("conversation turn cannot be serialized", exception);
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private long longValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int integerValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return 0;
        }
        return Math.max(0, Integer.parseInt(String.valueOf(value)));
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
