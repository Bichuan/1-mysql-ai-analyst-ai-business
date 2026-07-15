package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.BusinessMetadataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlAuditServiceImplTest {

    private SqlAuditServiceImpl sqlAuditService;

    @BeforeEach
    void setUp() {
        BusinessMetadataProperties metadata = new BusinessMetadataProperties();
        metadata.setTables(List.of(table("biz_customer"), table("biz_order"),
                table("biz_product"), table("biz_order_item")));
        sqlAuditService = new SqlAuditServiceImpl(metadata);
    }

    @Test
    void shouldAppendDefaultLimitToSafeSelect() {
        String auditedSql = sqlAuditService.auditAndNormalize("SELECT customer_name FROM biz_customer");

        assertThat(auditedSql).containsIgnoringCase("SELECT customer_name FROM biz_customer")
                .containsIgnoringCase("LIMIT 1000");
    }

    @Test
    void shouldCapLargeLimit() {
        String auditedSql = sqlAuditService.auditAndNormalize("SELECT * FROM biz_order LIMIT 5000");

        assertThat(auditedSql).containsIgnoringCase("LIMIT 1000");
        assertThat(auditedSql).doesNotContainIgnoringCase("LIMIT 5000");
    }

    @Test
    void shouldRejectNonSelectAndMultipleStatements() {
        assertRejected("DELETE FROM biz_customer");
        assertRejected("SELECT * FROM biz_customer; DELETE FROM biz_customer");
    }

    @Test
    void shouldRejectUnknownTableAndDangerousCapabilities() {
        assertRejected("SELECT * FROM sys_user");
        assertRejected("SELECT LOAD_FILE('/etc/passwd') FROM biz_customer");
        assertRejected("SELECT * FROM biz_customer INTO OUTFILE '/tmp/data.txt'");
        assertRejected("SELECT SLEEP(5) FROM biz_customer");
        assertRejected("SELECT BENCHMARK(1000, MD5('x')) FROM biz_customer");
    }

    @Test
    void shouldAllowJoinAndCteWhenAllBaseTablesAreWhitelisted() {
        String joinSql = sqlAuditService.auditAndNormalize("""
                SELECT c.customer_name, SUM(o.amount) AS total_amount
                FROM biz_customer c JOIN biz_order o ON c.id = o.customer_id
                GROUP BY c.id, c.customer_name
                """);
        String cteSql = sqlAuditService.auditAndNormalize("""
                WITH completed_orders AS (
                    SELECT customer_id, amount FROM biz_order WHERE status = 'COMPLETED'
                )
                SELECT c.customer_name, SUM(o.amount) AS total_amount
                FROM biz_customer c JOIN completed_orders o ON c.id = o.customer_id
                GROUP BY c.id, c.customer_name
                """);

        assertThat(joinSql).containsIgnoringCase("JOIN biz_order").containsIgnoringCase("LIMIT 1000");
        assertThat(cteSql).containsIgnoringCase("WITH completed_orders").containsIgnoringCase("LIMIT 1000");
    }

    @Test
    void shouldRejectBlankMalformedUnionAndInvalidLimit() {
        assertRejected("   ");
        assertRejected("SELECT FROM biz_customer");
        assertRejected("SELECT id FROM biz_customer UNION SELECT id FROM biz_order");
        assertRejected("SELECT id FROM biz_customer LIMIT -1");
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> sqlAuditService.auditAndNormalize(sql))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.SQL_AUDIT_FAILED);
    }

    private BusinessMetadataProperties.Table table(String name) {
        BusinessMetadataProperties.Table table = new BusinessMetadataProperties.Table();
        table.setName(name);
        return table;
    }
}
