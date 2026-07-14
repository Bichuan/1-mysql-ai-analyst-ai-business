package com.aianalyst.service.impl;

import com.aianalyst.common.RedisKeyPrefix;
import com.aianalyst.service.QueryCacheService;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.vo.QueryResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Redis 语义缓存实现。缓存值是完整的 QueryResultVO，但其中 rows 在进入本服务前已经脱敏。
 */
@Service
public class RedisQueryCacheServiceImpl implements QueryCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisQueryCacheServiceImpl.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final QueryMetricsService queryMetricsService;

    public RedisQueryCacheServiceImpl(StringRedisTemplate stringRedisTemplate,
                                      ObjectMapper objectMapper,
                                      QueryMetricsService queryMetricsService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.queryMetricsService = queryMetricsService;
    }

    @Override
    public Optional<QueryResultVO> get(Long userId, String question) {
        String key = cacheKey(userId, question);
        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(cachedValue)) {
                queryMetricsService.recordCacheMiss();
                return Optional.empty();
            }
            QueryResultVO result = objectMapper.readValue(cachedValue, QueryResultVO.class);
            queryMetricsService.recordCacheHit();
            return Optional.of(result);
        } catch (JsonProcessingException | RuntimeException exception) {
            // Redis 不可用或缓存内容损坏时降级为未命中，主查询链路仍可正常访问模型和数据库。
            queryMetricsService.recordCacheFallback();
            log.warn("Query cache read failed; falling back to normal query flow. key={}, cause={}",
                    key, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long userId, String question, QueryResultVO result) {
        String key = cacheKey(userId, question);
        try {
            // 只有 DataQueryService 在脱敏、总结完成后才调用此方法，避免原始 rows 写入 Redis。
            String cacheValue = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(key, cacheValue, CACHE_TTL);
        } catch (JsonProcessingException | RuntimeException exception) {
            // 写缓存是性能优化而不是业务前提；失败只能记录日志，不能把成功查询改成失败响应。
            log.warn("Query cache write failed; query result will not be cached. key={}, cause={}",
                    key, exception.getClass().getSimpleName());
        }
    }

    public static String cacheKey(Long userId, String question) {
        String normalizedQuestion = normalizeQuestion(question);
        // MD5 在这里仅用于缩短 Redis Key、避免直接暴露问题文本，不承担密码学安全职责。
        String questionHash = DigestUtils.md5DigestAsHex(normalizedQuestion.getBytes(StandardCharsets.UTF_8));
        return RedisKeyPrefix.QUERY_CACHE + userId + ':' + questionHash;
    }

    static String normalizeQuestion(String question) {
        String value = question == null ? "" : question.trim();
        return value.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
