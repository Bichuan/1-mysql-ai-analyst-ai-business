package com.aianalyst.service.impl;

import com.aianalyst.config.DeepSeekProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DeepSeekChatServiceImplTest {

    @Test
    void shouldRejectBlankPromptBeforeCreatingModelClient() {
        DeepSeekChatServiceImpl chatService = new DeepSeekChatServiceImpl(new DeepSeekProperties());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> chatService.generate("   "))
                .withMessage("prompt must not be blank");
    }

    @Test
    void shouldReportMissingApiKeyWithoutSendingNetworkRequest() {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey(" ");
        DeepSeekChatServiceImpl chatService = new DeepSeekChatServiceImpl(properties);

        assertThatIllegalStateException()
                .isThrownBy(() -> chatService.generate("生成一条安全查询"))
                .withMessageContaining("DeepSeek API key is not configured");
    }
}
