package com.aianalyst.service;

import com.aianalyst.service.impl.RedisQueryCacheServiceImpl;
import com.aianalyst.vo.QueryResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in integration test proving that the query cache uses the configured Redis logical database. */
@SpringBootTest
@EnabledIfSystemProperty(named = "runRedisIT", matches = "true")
class RedisQueryCacheIntegrationTest {

    @Autowired
    private QueryCacheService queryCacheService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final Long testUserId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    private final String question = "Day14 Redis 缓存连通性检查";

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete(RedisQueryCacheServiceImpl.cacheKey(testUserId, question));
    }

    @Test
    void shouldUseRedisDatabaseTwoAndRoundTripCachedResult() {
        assertThat(redisConnectionFactory).isInstanceOf(LettuceConnectionFactory.class);
        assertThat(((LettuceConnectionFactory) redisConnectionFactory).getDatabase()).isEqualTo(2);

        QueryResultVO expected = new QueryResultVO(question, "SELECT 1",
                List.of(Map.of("email", "t***@gmail.com")), 1, "本次查询返回 1 条数据", false);
        queryCacheService.put(testUserId, question, expected);

        assertThat(queryCacheService.get(testUserId, question)).contains(expected);
    }
}
