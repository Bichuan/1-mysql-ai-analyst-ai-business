package com.aianalyst.controller;

import com.aianalyst.common.Result;
import com.aianalyst.security.SecurityUser;
import com.aianalyst.service.UserService;
import com.aianalyst.vo.UserVO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated user endpoints. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> currentUser(@AuthenticationPrincipal SecurityUser securityUser) {
        return Result.success(userService.getCurrentUser(securityUser.getId()));
    }
}
