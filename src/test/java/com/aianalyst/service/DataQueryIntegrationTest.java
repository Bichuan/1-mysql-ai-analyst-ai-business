package com.aianalyst.service;

import com.aianalyst.service.impl.RedisRateLimitServiceImpl;
import com.aianalyst.vo.QueryResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in paid integration test that performs a real read-only business query. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runDataQueryIT", matches = "true")
class DataQueryIntegrationTest {

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Long testUserId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);

    @AfterEach
    void cleanUpRateLimitKey() {
        stringRedisTemplate.delete(RedisRateLimitServiceImpl.rateLimitKey(testUserId));
    }

    @Test
    void shouldGenerateAuditAndExecuteReadOnlyQuery() {
        QueryResultVO result = dataQueryService.query(testUserId, "查询前5个客户的名称、等级和地区");

        assertThat(result.sql().trim()).startsWithIgnoringCase("SELECT");
        assertThat(result.rows()).isNotEmpty();
        assertThat(result.rowCount()).isEqualTo(result.rows().size());
        assertThat(result.rowCount()).isLessThanOrEqualTo(1_000);
    }
}
