package com.aianalyst.controller;

import com.aianalyst.entity.User;
import com.aianalyst.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in end-to-end authentication test against the locally configured MySQL instance.
 * It creates and removes one uniquely named test user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "runAuthIT", matches = "true")
class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @LocalServerPort
    private int port;

    private String testUsername;

    @AfterEach
    void cleanUpTestUser() {
        if (testUsername != null) {
            userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getUsername, testUsername));
        }
    }

    @Test
    void shouldRegisterLoginAndAuthorizeWithJwt() {
        testUsername = "it_user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "IntegrationTestPassword2026";

        ResponseEntity<JsonNode> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register"),
                jsonRequest(Map.of(
                        "username", testUsername,
                        "password", password,
                        "nickname", "认证测试用户",
                        "email", testUsername + "@example.com")),
                JsonNode.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerResponse.getBody().path("code").asInt()).isZero();
        assertThat(registerResponse.getBody().path("data").path("username").asText()).isEqualTo(testUsername);

        ResponseEntity<JsonNode> loginResponse = restTemplate.postForEntity(
                url("/api/auth/login"),
                jsonRequest(Map.of("username", testUsername, "password", password)),
                JsonNode.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        ResponseEntity<JsonNode> anonymousResponse = restTemplate.getForEntity(url("/api/users/me"), JsonNode.class);
        assertThat(anonymousResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymousResponse.getBody().path("code").asInt()).isEqualTo(40100);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> authenticatedResponse = restTemplate.exchange(
                url("/api/users/me"), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(authenticatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authenticatedResponse.getBody().path("code").asInt()).isZero();
        assertThat(authenticatedResponse.getBody().path("data").path("username").asText()).isEqualTo(testUsername);
    }

    private HttpEntity<Map<String, String>> jsonRequest(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
