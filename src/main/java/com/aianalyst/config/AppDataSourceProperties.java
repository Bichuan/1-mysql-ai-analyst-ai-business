package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the system database and the read-only business database.
 * Passwords are supplied only through a local ignored configuration file or environment variables.
 */
@ConfigurationProperties(prefix = "app.datasource")
public class AppDataSourceProperties {

    private Connection system = new Connection();
    private Connection business = new Connection();

    public Connection getSystem() {
        return system;
    }

    public void setSystem(Connection system) {
        this.system = system;
    }

    public Connection getBusiness() {
        return business;
    }

    public void setBusiness(Connection business) {
        this.business = business;
    }

    public static class Connection {

        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private int maximumPoolSize = 10;
        private long connectionTimeout = 10_000L;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }
}
