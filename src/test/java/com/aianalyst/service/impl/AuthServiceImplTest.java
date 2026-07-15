package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.dto.LoginRequest;
import com.aianalyst.dto.RegisterRequest;
import com.aianalyst.entity.User;
import com.aianalyst.mapper.UserMapper;
import com.aianalyst.security.JwtTokenService;
import com.aianalyst.vo.LoginVO;
import com.aianalyst.vo.UserVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldRegisterUserWithEncodedPasswordAndSafeDefaults() {
        RegisterRequest request = registerRequest();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Test123456")).thenReturn("encoded-password");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        });

        UserVO result = authService.register(request);

        // 注册接口不能信任客户端传入角色和状态，Service 必须统一落为普通启用用户。
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertThat(inserted.getPassword()).isEqualTo("encoded-password");
        assertThat(inserted.getRole()).isEqualTo("USER");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.username()).isEqualTo("analyst_01");
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void shouldRejectDuplicateUsernameBeforeEncodingOrInsert() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        assertBusinessError(() -> authService.register(registerRequest()), "用户名已存在");

        verify(passwordEncoder, never()).encode(any());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void shouldLoginAndReturnServerSignedToken() {
        User user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Test123456", "encoded-password")).thenReturn(true);
        when(jwtTokenService.createToken(user)).thenReturn("signed-jwt");
        when(jwtTokenService.getExpirationSeconds()).thenReturn(7_200L);

        LoginVO result = authService.login(loginRequest("Test123456"));

        assertThat(result.accessToken()).isEqualTo("signed-jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(7_200L);
        assertThat(result.user().username()).isEqualTo("analyst_01");
    }

    @Test
    void shouldRejectUnknownUsernameWithoutCheckingPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertBusinessError(() -> authService.login(loginRequest("Test123456")), "用户名或密码错误");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenService, never()).createToken(any());
    }

    @Test
    void shouldRejectWrongPasswordWithoutIssuingToken() {
        User user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("WrongPassword", "encoded-password")).thenReturn(false);

        assertBusinessError(() -> authService.login(loginRequest("WrongPassword")), "用户名或密码错误");

        verify(jwtTokenService, never()).createToken(any());
    }

    @Test
    void shouldRejectDisabledAccountWithoutIssuingToken() {
        User user = activeUser();
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Test123456", "encoded-password")).thenReturn(true);

        assertBusinessError(() -> authService.login(loginRequest("Test123456")), "账号已被禁用");

        verify(jwtTokenService, never()).createToken(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldBindLoginSqlInjectionPayloadAsQueryParameter() {
        String injectionPayload = "' OR 1=1 --";
        // 纯 Mockito 测试没有 MyBatis 启动过程，需要显式初始化 Lambda 列缓存才能展开 SQL 片段。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "day25-test"), User.class);
        when(userMapper.selectOne(any())).thenReturn(null);

        assertBusinessError(
                () -> authService.login(loginRequest(injectionPayload, "irrelevant-password")),
                "用户名或密码错误");

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<User> wrapper = wrapperCaptor.getValue();
        // MyBatis Plus 生成占位符并把攻击字符串放进参数 Map，不能把它拼到 SQL 结构中。
        assertThat(wrapper.getSqlSegment()).contains("#{").doesNotContain(injectionPayload);
        assertThat(wrapper.getParamNameValuePairs()).containsValue(injectionPayload);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private void assertBusinessError(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.BUSINESS_ERROR);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("analyst_01");
        request.setPassword("Test123456");
        request.setNickname("测试分析员");
        request.setEmail("analyst01@example.com");
        return request;
    }

    private LoginRequest loginRequest(String password) {
        return loginRequest("analyst_01", password);
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private User activeUser() {
        User user = new User();
        user.setId(42L);
        user.setUsername("analyst_01");
        user.setPassword("encoded-password");
        user.setNickname("测试分析员");
        user.setEmail("analyst01@example.com");
        user.setRole("USER");
        user.setStatus(1);
        return user;
    }
}
