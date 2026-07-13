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
 * 双数据源隔离：系统库保存账号、历史等系统数据；业务库只供 AI 查询企业数据。
 * 这不是仅靠代码约定的隔离，业务库账号在 MySQL 层也只有 SELECT 权限。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppDataSourceProperties.class)
public class DataSourceConfig {

    @Bean(name = "systemDataSource", destroyMethod = "close")
    // MyBatis-Plus 的 Mapper 默认使用系统库，避免误把系统写操作指向业务库。
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
        // AI 返回的列并不固定，因此动态 SQL 不定义 Mapper，而是统一走这个具名只读 JdbcTemplate。
        return new JdbcTemplate(businessDataSource);
    }

    private HikariDataSource createDataSource(AppDataSourceProperties.Connection properties, String poolName) {
        // 两个连接池分别使用各自账号；即使上层审核遗漏，业务库账号也无法执行写操作。
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
