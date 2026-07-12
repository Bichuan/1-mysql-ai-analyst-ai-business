package com.aianalyst.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT settings. The secret must be supplied from ignored local configuration or an environment variable. */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;
    private long expirationSeconds = 7_200L;
    private String issuer = "ai-data-analyst";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
