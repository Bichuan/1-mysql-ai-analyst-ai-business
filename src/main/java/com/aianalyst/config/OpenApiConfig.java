package com.aianalyst.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/** OpenAPI metadata and the JWT request-header scheme displayed by Knife4j. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "BearerAuth";

    @Bean
    public OpenAPI aiDataAnalystOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 企业数据分析助手 API")
                        .description("企业数据查询系统后端接口文档。登录后使用右上角授权按钮配置 JWT Bearer Token。")
                        .version("v1.0.0")
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                // APIKEY is used here because Knife4j otherwise sends a header named "BearerAuth".
                                // The actual HTTP header must remain the standard Authorization header.
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(HttpHeaders.AUTHORIZATION)
                                .description("填写 Bearer <accessToken>")));
    }
}
