package com.aianalyst.service;

import com.aianalyst.dto.LoginRequest;
import com.aianalyst.dto.RegisterRequest;
import com.aianalyst.vo.LoginVO;
import com.aianalyst.vo.UserVO;

public interface AuthService {

    UserVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);
}
