package com.aianalyst.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in paid API check. The API key may be supplied by a local YAML file or an environment variable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runDeepSeekIT", matches = "true")
class DeepSeekConnectionIntegrationTest {

    @Autowired
    private DeepSeekChatService deepSeekChatService;

    @Test
    void shouldReceiveResponseFromDeepSeek() {
        String response = deepSeekChatService.generate("请只输出 PONG，不要添加任何其他内容。");
        assertThat(response).containsIgnoringCase("PONG");
    }
}
