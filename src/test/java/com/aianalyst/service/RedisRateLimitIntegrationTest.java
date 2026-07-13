package com.aianalyst.service;

import com.aianalyst.service.impl.RedisRateLimitServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in integration test against the locally configured Redis instance. */
@SpringBootTest
@EnabledIfSystemProperty(named = "runRedisIT", matches = "true")
class RedisRateLimitIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RateLimitService rateLimitService;

    private final Long testUserId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.delete(RedisRateLimitServiceImpl.rateLimitKey(testUserId));
    }

    @Test
    void shouldConnectToRedisAndRejectTheSixthRequestInOneMinute() {
        String pong = stringRedisTemplate.execute(RedisConnection::ping);
        assertThat(pong).isEqualTo("PONG");

        for (int request = 0; request < 5; request++) {
            assertThat(rateLimitService.tryAcquire(testUserId)).isTrue();
        }
        assertThat(rateLimitService.tryAcquire(testUserId)).isFalse();
    }
}
