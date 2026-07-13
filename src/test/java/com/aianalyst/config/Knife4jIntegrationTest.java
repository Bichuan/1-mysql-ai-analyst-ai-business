package com.aianalyst.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in verification that documentation resources remain publicly reachable
 * while business APIs continue to be protected by the security filter chain.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "runDocsIT", matches = "true")
class Knife4jIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void shouldExposeKnife4jPageAndOpenApiSpecificationWithoutAuthentication() {
        ResponseEntity<String> documentResponse = restTemplate.getForEntity(url("/api/doc.html"), String.class);
        assertThat(documentResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documentResponse.getBody()).contains("knife4j-vue");

        ResponseEntity<JsonNode> specificationResponse = restTemplate.getForEntity(url("/api/v3/api-docs"), JsonNode.class);
        assertThat(specificationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode paths = specificationResponse.getBody().path("paths");
        assertThat(paths.has("/auth/login")).isTrue();
        assertThat(paths.has("/users/me")).isTrue();
        assertThat(paths.has("/queries/generate-sql")).isTrue();
        JsonNode querySecurity = paths.path("/queries/generate-sql").path("post").path("security");
        assertThat(querySecurity.isArray()).isTrue();
        assertThat(querySecurity.toString()).contains(OpenApiConfig.BEARER_AUTH_SCHEME);
        JsonNode bearerScheme = specificationResponse.getBody()
                .path("components")
                .path("securitySchemes")
                .path(OpenApiConfig.BEARER_AUTH_SCHEME);
        assertThat(bearerScheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(bearerScheme.path("in").asText()).isEqualTo("header");
        assertThat(bearerScheme.path("name").asText()).isEqualTo("Authorization");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
