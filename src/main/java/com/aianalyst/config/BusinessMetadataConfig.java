package com.aianalyst.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Loads business-metadata.yml into a type-safe object graph. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BusinessMetadataProperties.class)
public class BusinessMetadataConfig {
}
