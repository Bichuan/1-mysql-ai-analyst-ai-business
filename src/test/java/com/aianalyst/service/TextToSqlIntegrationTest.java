package com.aianalyst.service;

import com.aianalyst.service.impl.RedisRateLimitServiceImpl;
import com.aianalyst.vo.SqlGenerationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in paid test that generates SQL without issuing it to MySQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runTextToSqlIT", matches = "true")
class TextToSqlIntegrationTest {

    @Autowired
    private SqlGenerationService sqlGenerationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Long testUserId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

    @AfterEach
    void cleanUpRateLimitKey() {
        stringRedisTemplate.delete(RedisRateLimitServiceImpl.rateLimitKey(testUserId));
    }

    @Test
    void shouldGenerateSelectSqlForBusinessQuestion() {
        SqlGenerationVO result = sqlGenerationService.generate(testUserId, "查询今年销售额最高的10个客户");

        assertThat(result.question()).isEqualTo("查询今年销售额最高的10个客户");
        assertThat(result.sql().trim()).startsWithIgnoringCase("SELECT");
        assertThat(result.sql()).doesNotContain("```");
    }
}
