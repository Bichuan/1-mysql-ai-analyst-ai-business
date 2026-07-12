package com.aianalyst.vo;

/** Login response containing a bearer access token and safe user information. */
public record LoginVO(String accessToken, String tokenType, long expiresIn, UserVO user) {
}
