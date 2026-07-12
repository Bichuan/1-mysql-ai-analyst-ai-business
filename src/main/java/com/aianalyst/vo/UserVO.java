package com.aianalyst.vo;

import com.aianalyst.entity.User;

/** User information safe to expose to API clients. */
public record UserVO(Long id, String username, String nickname, String email, String role) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getRole());
    }
}
