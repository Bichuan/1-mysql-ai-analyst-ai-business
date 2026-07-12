package com.aianalyst.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in verification for a locally provisioned MySQL instance.
 * Run with: .\mvnw.cmd test -DrunDbIT=true
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "runDbIT", matches = "true")
class DataSourceIntegrationTest {

    private final DataSource systemDataSource;
    private final JdbcTemplate businessJdbcTemplate;

    @Autowired
    DataSourceIntegrationTest(@Qualifier("systemDataSource") DataSource systemDataSource,
                              @Qualifier("businessJdbcTemplate") JdbcTemplate businessJdbcTemplate) {
        this.systemDataSource = systemDataSource;
        this.businessJdbcTemplate = businessJdbcTemplate;
    }

    @Test
    void shouldConnectToSystemDatabase() throws Exception {
        try (var connection = systemDataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void shouldReadSeededBusinessData() {
        assertThat(businessJdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_customer", Integer.class))
                .isEqualTo(100);
        assertThat(businessJdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_product", Integer.class))
                .isEqualTo(50);
        assertThat(businessJdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_order", Integer.class))
                .isEqualTo(1000);
        assertThat(businessJdbcTemplate.queryForObject("SELECT COUNT(*) FROM biz_order_item", Integer.class))
                .isEqualTo(2000);
    }

    @Test
    void shouldRejectWriteSqlFromReadOnlyBusinessAccount() {
        assertThatThrownBy(() -> businessJdbcTemplate.update("UPDATE biz_product SET stock = stock WHERE 1 = 0"))
                .isInstanceOf(DataAccessException.class);
    }
}
