package com.aianalyst.service.impl;

import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.vo.QueryResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisQueryCacheServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private QueryMetricsService queryMetricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadCachedMaskedResultAndRecordHitMetric() throws Exception {
        RedisQueryCacheServiceImpl service = new RedisQueryCacheServiceImpl(
                stringRedisTemplate, objectMapper, queryMetricsService);
        QueryResultVO cachedResult = new QueryResultVO("查询客户", "SELECT id FROM biz_customer",
                List.of(Map.of("email", "t***@gmail.com")), 1, "本次查询返回 1 条数据", false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisQueryCacheServiceImpl.cacheKey(7L, "查询客户")))
                .thenReturn(objectMapper.writeValueAsString(cachedResult));

        QueryResultVO result = service.get(7L, "查询客户").orElseThrow();

        assertThat(result.rows()).containsExactlyElementsOf(cachedResult.rows());
        assertThat(result.rows().get(0).get("email")).isEqualTo("t***@gmail.com");
        verify(queryMetricsService).recordCacheHit();
    }

    @Test
    void shouldRecordMissForEmptyCacheValue() {
        RedisQueryCacheServiceImpl service = new RedisQueryCacheServiceImpl(
                stringRedisTemplate, objectMapper, queryMetricsService);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        assertThat(service.get(7L, "查询客户")).isEmpty();

        verify(queryMetricsService).recordCacheMiss();
    }

    @Test
    void shouldWriteResultWithThirtyMinuteTtl() {
        RedisQueryCacheServiceImpl service = new RedisQueryCacheServiceImpl(
                stringRedisTemplate, objectMapper, queryMetricsService);
        QueryResultVO result = new QueryResultVO("查询客户", "SELECT id FROM biz_customer",
                List.of(Map.of("customer_name", "客户A")), 1, "本次查询返回 1 条数据", false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service.put(7L, "查询客户", result);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(RedisQueryCacheServiceImpl.cacheKey(7L, "查询客户"));
        assertThat(valueCaptor.getValue()).contains("客户A");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldSeparateUsersAndNormalizeEquivalentQuestions() {
        String sameQuestionKey = RedisQueryCacheServiceImpl.cacheKey(7L, " 查询   客户 ");

        assertThat(sameQuestionKey).isEqualTo(RedisQueryCacheServiceImpl.cacheKey(7L, "查询 客户"));
        assertThat(sameQuestionKey).isNotEqualTo(RedisQueryCacheServiceImpl.cacheKey(8L, "查询 客户"));
    }

    @Test
    void shouldFallbackToMissWhenRedisReadFails() {
        RedisQueryCacheServiceImpl service = new RedisQueryCacheServiceImpl(
                stringRedisTemplate, objectMapper, queryMetricsService);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(service.get(7L, "查询客户")).isEmpty();
        assertThatCode(() -> service.put(7L, "查询客户", new QueryResultVO(
                "查询客户", "SELECT 1", List.of(), 0, "无数据", false))).doesNotThrowAnyException();
        verify(queryMetricsService).recordCacheFallback();
    }
}
