package com.aianalyst.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用外部化 DeepSeek 配置。API Key 只放在本地忽略文件或环境变量中，
 * 不能硬编码在仓库，更不能提交到 Git。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfig {
}
