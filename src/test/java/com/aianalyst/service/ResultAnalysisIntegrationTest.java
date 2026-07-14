package com.aianalyst.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in paid test: the model receives only a deliberately masked sample row. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runResultAnalysisIT", matches = "true")
class ResultAnalysisIntegrationTest {

    @Autowired
    private ResultAnalysisService resultAnalysisService;

    @Test
    void shouldGenerateSummaryForMaskedResultRows() {
        String summary = resultAnalysisService.analyze(
                "查询销售额最高的客户",
                "SELECT customer_name, sales FROM biz_customer ORDER BY sales DESC LIMIT 1",
                List.of(Map.of("customer_name", "企业客户001", "email", "t***@gmail.com", "sales", 19081.2)),
                1);

        assertThat(summary).isNotBlank().doesNotContain("AI 总结暂不可用");
    }
}
