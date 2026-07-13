package com.aianalyst.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每个请求最多执行一次的 JWT 过滤器：从 Authorization 头解析 Bearer Token，
 * 校验通过后把用户放入当前请求的 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SecurityUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   SecurityUserDetailsService userDetailsService) {
        this.jwtTokenService = jwtTokenService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        // 已存在认证信息时不覆盖，避免与后续扩展的其他认证方式冲突。
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtTokenService.isTokenValid(token)) {
            JwtTokenService.JwtUserClaims claims = jwtTokenService.parseToken(token);
            // Token 只证明签发时的身份；这里再查询用户状态，能让被禁用账号立即失效。
            UserDetails userDetails = userDetailsService.loadUserByUsername(claims.username());
            if (userDetails.isEnabled()) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 只接受标准 Authorization: Bearer <token> 格式，不从 URL 参数读取 Token，避免日志泄露。
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
