package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings for the Redis-backed active conversation working set. */
@ConfigurationProperties(prefix = "app.conversation")
public class ConversationProperties {

    private Duration redisTtl = Duration.ofHours(2);
    private int recentTurnCount = 3;

    public Duration getRedisTtl() {
        return redisTtl;
    }

    public void setRedisTtl(Duration redisTtl) {
        if (redisTtl == null || redisTtl.isZero() || redisTtl.isNegative()) {
            throw new IllegalArgumentException("app.conversation.redis-ttl must be positive");
        }
        this.redisTtl = redisTtl;
    }

    public int getRecentTurnCount() {
        return recentTurnCount;
    }

    public void setRecentTurnCount(int recentTurnCount) {
        if (recentTurnCount < 3 || recentTurnCount > 5) {
            throw new IllegalArgumentException("app.conversation.recent-turn-count must be between 3 and 5");
        }
        this.recentTurnCount = recentTurnCount;
    }
}
