package com.aianalyst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * AI 企业数据分析助手的应用启动入口。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class AiDataAnalystApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDataAnalystApplication.class, args);
    }
}
