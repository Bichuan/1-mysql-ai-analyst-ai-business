package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded executor settings for model calls and asynchronous query orchestration. */
@ConfigurationProperties(prefix = "app.async")
public class AsyncExecutorProperties {

    private Pool llmCore = new Pool(4, 6, 20, "llm-core-");
    private Pool llmAnalysis = new Pool(1, 2, 10, "llm-analysis-");
    private Pool queryOrchestration = new Pool(4, 8, 50, "query-orchestration-");

    public Pool getLlmCore() {
        return llmCore;
    }

    public void setLlmCore(Pool llmCore) {
        this.llmCore = requirePool(llmCore, "app.async.llm-core");
    }

    public Pool getLlmAnalysis() {
        return llmAnalysis;
    }

    public void setLlmAnalysis(Pool llmAnalysis) {
        this.llmAnalysis = requirePool(llmAnalysis, "app.async.llm-analysis");
    }

    public Pool getQueryOrchestration() {
        return queryOrchestration;
    }

    public void setQueryOrchestration(Pool queryOrchestration) {
        this.queryOrchestration = requirePool(queryOrchestration, "app.async.query-orchestration");
    }

    void validate() {
        llmCore.validate("app.async.llm-core");
        llmAnalysis.validate("app.async.llm-analysis");
        queryOrchestration.validate("app.async.query-orchestration");
    }

    private Pool requirePool(Pool pool, String propertyName) {
        if (pool == null) {
            throw new IllegalArgumentException(propertyName + " must not be null");
        }
        return pool;
    }

    public static class Pool {

        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private String threadNamePrefix;

        public Pool() {
        }

        Pool(int corePoolSize, int maxPoolSize, int queueCapacity, String threadNamePrefix) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.threadNamePrefix = threadNamePrefix;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        void validate(String propertyName) {
            if (corePoolSize <= 0) {
                throw new IllegalArgumentException(propertyName + ".core-pool-size must be positive");
            }
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(
                        propertyName + ".max-pool-size must be greater than or equal to core-pool-size");
            }
            if (queueCapacity < 0) {
                throw new IllegalArgumentException(propertyName + ".queue-capacity must not be negative");
            }
            if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
                throw new IllegalArgumentException(propertyName + ".thread-name-prefix must not be blank");
            }
        }
    }
}
