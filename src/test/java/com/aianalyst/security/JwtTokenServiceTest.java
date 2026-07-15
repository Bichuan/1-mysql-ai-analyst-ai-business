package com.aianalyst.security;

import com.aianalyst.config.JwtProperties;
import com.aianalyst.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void shouldCreateAndParseSignedToken() {
        JwtProperties properties = properties("ai-data-analyst-test", 7_200L);
        JwtTokenService tokenService = new JwtTokenService(properties);
        User user = user();

        String token = tokenService.createToken(user);
        JwtTokenService.JwtUserClaims claims = tokenService.parseToken(token);

        assertThat(tokenService.isTokenValid(token)).isTrue();
        assertThat(claims.userId()).isEqualTo(100L);
        assertThat(claims.username()).isEqualTo("analyst_user");
        assertThat(claims.role()).isEqualTo("USER");
    }

    @Test
    void shouldRejectTamperedAndMalformedTokens() {
        JwtTokenService tokenService = new JwtTokenService(properties("ai-data-analyst-test", 7_200L));
        String token = tokenService.createToken(user());
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThat(tokenService.isTokenValid(tampered)).isFalse();
        assertThat(tokenService.isTokenValid("not-a-jwt")).isFalse();
        assertThat(tokenService.isTokenValid(null)).isFalse();
    }

    @Test
    void shouldRejectTokenIssuedForAnotherApplication() {
        JwtTokenService issuerA = new JwtTokenService(properties("application-a", 7_200L));
        JwtTokenService issuerB = new JwtTokenService(properties("application-b", 7_200L));

        assertThat(issuerB.isTokenValid(issuerA.createToken(user()))).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenService tokenService = new JwtTokenService(properties("ai-data-analyst-test", -1L));

        assertThat(tokenService.isTokenValid(tokenService.createToken(user()))).isFalse();
    }

    @Test
    void shouldRejectSecretShorterThanThirtyTwoBytes() {
        JwtProperties properties = properties("ai-data-analyst-test", 7_200L);
        properties.setSecret("too-short");
        JwtTokenService tokenService = new JwtTokenService(properties);

        assertThatThrownBy(() -> tokenService.createToken(user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度不足 32 字节");
    }

    private JwtProperties properties(String issuer, long expirationSeconds) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("day24-test-secret-must-contain-at-least-thirty-two-bytes");
        properties.setIssuer(issuer);
        properties.setExpirationSeconds(expirationSeconds);
        return properties;
    }

    private User user() {
        User user = new User();
        user.setId(100L);
        user.setUsername("analyst_user");
        user.setRole("USER");
        return user;
    }
}
