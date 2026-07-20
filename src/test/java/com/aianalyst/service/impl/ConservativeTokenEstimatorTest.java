package com.aianalyst.service.impl;

import com.aianalyst.dto.ConversationTurnSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConservativeTokenEstimatorTest {

    private final ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();

    @Test
    void shouldEstimateChineseAndAsciiConservatively() {
        assertThat(estimator.estimate(null)).isZero();
        assertThat(estimator.estimate("查询华东")).isEqualTo(8);
        assertThat(estimator.estimate("customerName")).isEqualTo(3);
        assertThat(estimator.estimate("查询 customer_name"))
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    void shouldEstimateOnlyReusableConversationFields() {
        ConversationTurnSnapshot turn = new ConversationTurnSnapshot(
                1L, "那华东呢", "查询华东销售额", "销售额100万元",
                99L, "SUCCESS", LocalDateTime.of(2026, 7, 20, 9, 0));

        int tokens = estimator.estimateConversationContext(
                "历史摘要", "{\"metric\":\"销售额\"}", List.of(turn));

        assertThat(tokens).isGreaterThan(estimator.estimate("历史摘要"));
        assertThat(estimator.estimateConversationContext(null, null, List.of())).isZero();
    }
}
