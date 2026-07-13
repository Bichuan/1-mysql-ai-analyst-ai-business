package com.aianalyst.config;

import com.aianalyst.security.JwtAuthenticationFilter;
import com.aianalyst.security.RestAccessDeniedHandler;
import com.aianalyst.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 无状态 JWT 安全配置：服务端不保存 Session，每个请求都携带并校验 Token。
 * 适合前后端分离的 REST API，也便于后续横向扩容。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 是带盐的单向哈希；数据库只存哈希值，不能也不应还原明文密码。
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // 本项目不使用基于 Cookie 的 Session，关闭 CSRF 后由 JWT Bearer Token 承担身份校验。
                .csrf(csrf -> csrf.disable())
                // 禁止 Spring Security 创建会话，避免 JWT 与服务端 Session 混用。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 登录、健康检查和接口文档必须匿名可访问；其余接口默认要求登录。
                        .requestMatchers("/auth/**", "/health", "/error", "/doc.html", "/webjars/**",
                                "/swagger-ui/**", "/swagger-resources/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 先解析 JWT 并写入 SecurityContext，后续授权规则才能识别当前用户。
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
