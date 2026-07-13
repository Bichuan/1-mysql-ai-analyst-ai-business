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
