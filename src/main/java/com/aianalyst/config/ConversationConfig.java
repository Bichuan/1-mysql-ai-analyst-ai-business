package com.aianalyst.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables externalized conversation storage settings. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationProperties.class)
public class ConversationConfig {
}
