package com.aianalyst.vo;

import com.aianalyst.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** User information safe to expose to API clients. */
@Schema(description = "可以安全返回给客户端的用户信息，不包含密码")
public record UserVO(
        @Schema(description = "用户 ID", example = "2") Long id,
        @Schema(description = "用户名", example = "testuser01") String username,
        @Schema(description = "展示昵称", example = "测试用户") String nickname,
        @Schema(description = "邮箱", example = "testuser01@example.com") String email,
        @Schema(description = "角色：USER 或 ADMIN", example = "USER") String role) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getRole());
    }
}
