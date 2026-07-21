package com.aianalyst.service.impl;

import com.aianalyst.config.DeepSeekProperties;
import com.aianalyst.service.DeepSeekChatService;
import com.aianalyst.service.QueryMetricsService;
import com.aianalyst.service.TokenBudgetService;
import com.aianalyst.dto.TokenBudgetAssessment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 延迟创建模型：缺少本地 API Key 时应用仍可启动，只有真正调用 AI 时才报配置错误。
 * 同时关闭模型请求/响应日志，避免 Prompt、SQL 或查询结果被写入日志系统。
 */
@Service
public class DeepSeekChatServiceImpl implements DeepSeekChatService {

    private final DeepSeekProperties properties;
    private final TokenBudgetService tokenBudgetService;
    private final QueryMetricsService queryMetricsService;
    private volatile ChatModel chatModel;

    public DeepSeekChatServiceImpl(DeepSeekProperties properties,
                                   TokenBudgetService tokenBudgetService,
                                   QueryMetricsService queryMetricsService) {
        this.properties = properties;
        this.tokenBudgetService = tokenBudgetService;
        this.queryMetricsService = queryMetricsService;
    }

    @Override
    public String generate(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        TokenBudgetAssessment assessment;
        try {
            assessment = tokenBudgetService.requireWithinBudget(prompt);
        } catch (RuntimeException exception) {
            queryMetricsService.recordTokenBudgetRejected();
            throw exception;
        }
        queryMetricsService.recordModelPromptTokens(assessment.estimatedPromptTokens());
        return getChatModel().chat(prompt);
    }

    private ChatModel getChatModel() {
        ChatModel existing = chatModel;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            // 双重检查 + volatile：并发首请求时只创建一个模型客户端，后续请求复用它。
            if (chatModel == null) {
                chatModel = OpenAiChatModel.builder()
                        .baseUrl(properties.getBaseUrl())
                        .apiKey(requireApiKey())
                        .modelName(properties.getModelName())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        // Native HTTP timeout is the final stop even when an upper-layer Future is cancelled.
                        .timeout(properties.getTimeout())
                        // SQL semantic correction is managed explicitly; transport retries stay bounded separately.
                        .maxRetries(properties.getMaxRetries())
                        .build();
            }
            return chatModel;
        }
    }

    private String requireApiKey() {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DeepSeek API key is not configured. Set DEEPSEEK_API_KEY or ai.deepseek.api-key.");
        }
        return apiKey;
    }
}
