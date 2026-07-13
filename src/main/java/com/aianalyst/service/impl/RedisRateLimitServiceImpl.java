package com.aianalyst.service.impl;

import com.aianalyst.common.RedisKeyPrefix;
import com.aianalyst.service.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 固定窗口的单用户限流器。
 * GET、判断、DECR 必须在 Lua 脚本中原子执行；若拆成多次 Java 调用，高并发下会出现超额放行。
 */
@Service
public class RedisRateLimitServiceImpl implements RateLimitService {

    private static final int QUERY_LIMIT_PER_MINUTE = 5;
    private static final int WINDOW_SECONDS = 60;

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local remaining = redis.call('GET', KEYS[1])
            if not remaining then
                -- 第一次请求直接扣除一个令牌并设置 60 秒过期时间，当前请求允许通过。
                redis.call('SET', KEYS[1], ARGV[1] - 1, 'EX', ARGV[2])
                return 1
            end
            if tonumber(remaining) <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryAcquire(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Long acquired = stringRedisTemplate.execute(
                // Key 按用户隔离，避免一个高频用户影响其他用户的模型调用额度。
                ACQUIRE_SCRIPT,
                List.of(rateLimitKey(userId)),
                String.valueOf(QUERY_LIMIT_PER_MINUTE),
                String.valueOf(WINDOW_SECONDS));
        return Long.valueOf(1L).equals(acquired);
    }

    public static String rateLimitKey(Long userId) {
        return RedisKeyPrefix.QUERY_RATE_LIMIT + userId;
    }
}
