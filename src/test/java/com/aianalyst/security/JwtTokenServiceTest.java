package com.aianalyst.security;

import com.aianalyst.config.JwtProperties;
import com.aianalyst.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void shouldCreateAndParseSignedToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("day4-test-secret-must-contain-at-least-thirty-two-bytes");
        properties.setIssuer("ai-data-analyst-test");
        properties.setExpirationSeconds(7_200L);
        JwtTokenService tokenService = new JwtTokenService(properties);

        User user = new User();
        user.setId(100L);
        user.setUsername("analyst_user");
        user.setRole("USER");

        String token = tokenService.createToken(user);
        JwtTokenService.JwtUserClaims claims = tokenService.parseToken(token);

        assertThat(tokenService.isTokenValid(token)).isTrue();
        assertThat(claims.userId()).isEqualTo(100L);
        assertThat(claims.username()).isEqualTo("analyst_user");
        assertThat(claims.role()).isEqualTo("USER");
    }
}
