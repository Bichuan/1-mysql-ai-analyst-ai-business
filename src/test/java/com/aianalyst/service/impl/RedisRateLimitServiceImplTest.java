package com.aianalyst.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private RedisRateLimitServiceImpl rateLimitService;

    @Test
    void shouldAcquireTokenUsingUserScopedLuaScript() {
        // Lua 脚本把计数、过期时间设置合并成一次原子操作，测试同时锁定限额和窗口参数。
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(List.of("rate_limit:7")), eq("5"), eq("60"))).thenReturn(1L);

        assertThat(rateLimitService.tryAcquire(7L)).isTrue();

        verify(stringRedisTemplate).execute(any(RedisScript.class),
                eq(List.of("rate_limit:7")), eq("5"), eq("60"));
    }

    @Test
    void shouldRejectWhenWindowHasNoRemainingToken() {
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(List.of("rate_limit:7")), eq("5"), eq("60"))).thenReturn(0L);

        assertThat(rateLimitService.tryAcquire(7L)).isFalse();
    }

    @Test
    void shouldFailClosedWhenRedisReturnsNoScriptResult() {
        when(stringRedisTemplate.execute(any(RedisScript.class),
                eq(List.of("rate_limit:7")), eq("5"), eq("60"))).thenReturn(null);

        assertThat(rateLimitService.tryAcquire(7L)).isFalse();
    }

    @Test
    void shouldRequireAuthenticatedUserIdAndKeepKeysIsolated() {
        assertThatNullPointerException().isThrownBy(() -> rateLimitService.tryAcquire(null));
        assertThat(RedisRateLimitServiceImpl.rateLimitKey(7L)).isEqualTo("rate_limit:7");
        assertThat(RedisRateLimitServiceImpl.rateLimitKey(8L)).isEqualTo("rate_limit:8");
    }
}
