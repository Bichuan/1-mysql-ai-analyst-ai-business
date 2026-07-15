package com.aianalyst.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** Login response containing a bearer access token and safe user information. */
@Schema(description = "登录成功响应")
public record LoginVO(
        @Schema(description = "JWT 访问令牌；Knife4j 授权时需填写 Bearer + 空格 + 令牌") String accessToken,
        @Schema(description = "令牌类型", example = "Bearer") String tokenType,
        @Schema(description = "有效期，单位为秒", example = "7200") long expiresIn,
        @Schema(description = "当前登录用户的安全展示信息") UserVO user) {
}
