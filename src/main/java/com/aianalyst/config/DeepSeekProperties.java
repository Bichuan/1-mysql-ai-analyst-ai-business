package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Local configuration for the OpenAI-compatible DeepSeek Chat Completions API. */
@ConfigurationProperties(prefix = "ai.deepseek")
public class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String modelName = "deepseek-v4-pro";
    private double temperature = 0.0D;
    private int maxTokens = 2048;
    private int contextWindowTokens = 32_768;
    private double contextUsageLimit = 0.80D;
    private int tokenSafetyMargin = 256;
    private Duration timeout = Duration.ofSeconds(25);
    private int maxRetries = 0;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("ai.deepseek.max-tokens must be positive");
        }
        this.maxTokens = maxTokens;
    }

    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    public void setContextWindowTokens(int contextWindowTokens) {
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("ai.deepseek.context-window-tokens must be positive");
        }
        this.contextWindowTokens = contextWindowTokens;
    }

    public double getContextUsageLimit() {
        return contextUsageLimit;
    }

    public void setContextUsageLimit(double contextUsageLimit) {
        if (contextUsageLimit <= 0.0D || contextUsageLimit > 1.0D) {
            throw new IllegalArgumentException("ai.deepseek.context-usage-limit must be in (0, 1]");
        }
        this.contextUsageLimit = contextUsageLimit;
    }

    public int getTokenSafetyMargin() {
        return tokenSafetyMargin;
    }

    public void setTokenSafetyMargin(int tokenSafetyMargin) {
        if (tokenSafetyMargin < 0) {
            throw new IllegalArgumentException("ai.deepseek.token-safety-margin must not be negative");
        }
        this.tokenSafetyMargin = tokenSafetyMargin;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("ai.deepseek.timeout must be positive");
        }
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 0 || maxRetries > 1) {
            throw new IllegalArgumentException("ai.deepseek.max-retries must be between 0 and 1");
        }
        this.maxRetries = maxRetries;
    }
}
