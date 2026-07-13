package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.security.SecurityUser;
import com.aianalyst.service.UserService;
import com.aianalyst.vo.UserVO;
import com.aianalyst.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated user endpoints. */
@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "当前登录用户信息")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "必须在 Knife4j 的授权弹窗中配置 Bearer Token")
    public Result<UserVO> currentUser(@AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(userService.getCurrentUser(securityUser.getId()));
    }
}
