package com.aianalyst.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in paid test for the real model-based SQL correction path and read-only execution. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "runSqlCorrectionIT", matches = "true")
class SqlCorrectionIntegrationTest {

    @Autowired
    private TextToSqlService textToSqlService;

    @Autowired
    private SqlExecutionService sqlExecutionService;

    @Test
    void shouldRepairKnownColumnErrorAndExecuteCorrectedSql() {
        String failedSql = "SELECT customer_nam FROM biz_customer LIMIT 5";
        String correctedSql = textToSqlService.correctSql(
                "查询前5个客户的名称、等级和地区",
                failedSql,
                "Unknown column 'customer_nam' in 'field list'");

        List<Map<String, Object>> rows = sqlExecutionService.executeAuditedSelect(correctedSql);

        assertThat(correctedSql).startsWithIgnoringCase("SELECT");
        assertThat(rows).isNotEmpty();
    }
}
