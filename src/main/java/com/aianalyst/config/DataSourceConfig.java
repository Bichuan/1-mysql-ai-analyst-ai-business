package com.aianalyst.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Keeps the system database and business database physically separate.
 * The business datasource is exposed only as a named JdbcTemplate for audited dynamic SELECT SQL.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppDataSourceProperties.class)
public class DataSourceConfig {

    @Bean(name = "systemDataSource", destroyMethod = "close")
    @Primary
    @ConditionalOnProperty(prefix = "app.datasource.system", name = "jdbc-url")
    public HikariDataSource systemDataSource(AppDataSourceProperties properties) {
        return createDataSource(properties.getSystem(), "ai-analyst-system-pool");
    }

    @Bean(name = "businessDataSource", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.datasource.business", name = "jdbc-url")
    public HikariDataSource businessDataSource(AppDataSourceProperties properties) {
        return createDataSource(properties.getBusiness(), "ai-analyst-business-read-pool");
    }

    @Bean(name = "businessJdbcTemplate")
    @ConditionalOnBean(name = "businessDataSource")
    public JdbcTemplate businessJdbcTemplate(@Qualifier("businessDataSource") DataSource businessDataSource) {
        return new JdbcTemplate(businessDataSource);
    }

    private HikariDataSource createDataSource(AppDataSourceProperties.Connection properties, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setConnectionTimeout(properties.getConnectionTimeout());
        return new HikariDataSource(config);
    }
}
