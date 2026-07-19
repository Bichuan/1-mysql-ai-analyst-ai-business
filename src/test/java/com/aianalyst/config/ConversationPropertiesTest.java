package com.aianalyst.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationPropertiesTest {

    @Test
    void shouldUseSafeDefaultsAndAllowThreeToFiveRecentTurns() {
        ConversationProperties properties = new ConversationProperties();

        assertThat(properties.getRedisTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(properties.getRecentTurnCount()).isEqualTo(3);

        properties.setRecentTurnCount(5);
        assertThat(properties.getRecentTurnCount()).isEqualTo(5);
    }

    @Test
    void shouldRejectInvalidWorkingSetConfiguration() {
        ConversationProperties properties = new ConversationProperties();

        assertThatThrownBy(() -> properties.setRecentTurnCount(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setRecentTurnCount(6))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setRedisTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
