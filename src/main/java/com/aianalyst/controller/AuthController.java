package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.dto.LoginRequest;
import com.aianalyst.dto.RegisterRequest;
import com.aianalyst.service.AuthService;
import com.aianalyst.vo.LoginVO;
import com.aianalyst.vo.UserVO;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints. */
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "用户注册与 JWT 登录")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册用户", description = "创建一个角色为 USER 的企业数据分析助手账号")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码校验成功后返回 JWT accessToken")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
