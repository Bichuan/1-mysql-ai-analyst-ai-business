package com.aianalyst.service;

import com.aianalyst.entity.ConversationSession;
import com.aianalyst.entity.QueryHistory;
import com.aianalyst.entity.User;
import com.aianalyst.mapper.ConversationSessionMapper;
import com.aianalyst.mapper.QueryHistoryMapper;
import com.aianalyst.mapper.UserMapper;
import com.aianalyst.service.impl.RedisConversationContextStore;
import com.aianalyst.service.impl.RedisRateLimitServiceImpl;
import com.aianalyst.vo.QueryResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in paid integration test that performs a real read-only business query. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runDataQueryIT", matches = "true")
class DataQueryIntegrationTest {

    @Autowired
    private DataQueryService dataQueryService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConversationSessionMapper conversationSessionMapper;

    @Autowired
    private QueryHistoryMapper queryHistoryMapper;

    private Long testUserId;
    private String conversationId;

    @BeforeEach
    void createTestUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername("query_it_" + suffix);
        user.setPassword("integration-test-not-used");
        user.setNickname("查询集成测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        testUserId = user.getId();
    }

    @AfterEach
    void cleanUpTestData() {
        if (testUserId == null) {
            return;
        }
        stringRedisTemplate.delete(RedisRateLimitServiceImpl.rateLimitKey(testUserId));
        if (conversationId != null) {
            stringRedisTemplate.delete(RedisConversationContextStore.metaKey(testUserId, conversationId));
            stringRedisTemplate.delete(RedisConversationContextStore.turnsKey(testUserId, conversationId));
            ConversationSession session = conversationSessionMapper.selectByConversationId(conversationId);
            if (session != null) {
                conversationSessionMapper.deleteById(session.getId());
            }
        }
        queryHistoryMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<QueryHistory>lambdaQuery()
                .eq(QueryHistory::getUserId, testUserId));
        userMapper.deleteById(testUserId);
    }

    @Test
    void shouldGenerateAuditAndExecuteReadOnlyQuery() {
        QueryResultVO result = dataQueryService.query(testUserId, "查询前5个客户的名称、等级和地区");
        conversationId = result.conversationId();

        assertThat(result.sql().trim()).startsWithIgnoringCase("SELECT");
        assertThat(result.rows()).isNotEmpty();
        assertThat(result.rowCount()).isEqualTo(result.rows().size());
        assertThat(result.rowCount()).isLessThanOrEqualTo(1_000);
        assertThat(conversationId).isNotBlank();
        assertThat(waitForConversationTurn(Duration.ofSeconds(5))).isTrue();
    }

    private boolean waitForConversationTurn(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConversationSession session = conversationSessionMapper.selectByConversationId(conversationId);
            if (session != null && session.getCurrentTurn() != null && session.getCurrentTurn() >= 1) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
