package com.aianalyst.security;

import com.aianalyst.config.JwtProperties;
import com.aianalyst.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 负责 JWT 的签发与验签。Token 中只保存用户标识、用户名和角色等身份声明，
 * 不保存密码或数据库敏感信息；完整性依赖 HMAC 签名保证。
 */
@Service
public class JwtTokenService {

    private static final String USER_ID_CLAIM = "uid";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                // issuer 防止同一密钥下、来自其他系统的 Token 被误接受。
                .issuer(properties.getIssuer())
                .subject(user.getUsername())
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLE_CLAIM, user.getRole())
                .issuedAt(Date.from(now))
                // 过期时间由配置统一控制，避免在业务代码中散落魔法值。
                .expiration(Date.from(now.plusSeconds(properties.getExpirationSeconds())))
                .signWith(signingKey())
                .compact();
    }

    public JwtUserClaims parseToken(String token) {
        // verifyWith 会同时校验签名；parseSignedClaims 还会校验过期时间等标准声明。
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtUserClaims(
                claims.get(USER_ID_CLAIM, Long.class),
                claims.getSubject(),
                claims.get(ROLE_CLAIM, String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }

    private SecretKey signingKey() {
        String secret = properties.getSecret();
        // HMAC 密钥过短会显著降低安全性，因此启动/签发时直接拒绝不合规配置。
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥未配置或长度不足 32 字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record JwtUserClaims(Long userId, String username, String role) {
    }
}
