package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.entity.User;
import com.aianalyst.mapper.UserMapper;
import com.aianalyst.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void shouldReturnSafeCurrentUserView() {
        User user = new User();
        user.setId(7L);
        user.setUsername("analyst_07");
        user.setPassword("must-not-be-returned");
        user.setNickname("分析员七号");
        user.setEmail("analyst07@example.com");
        user.setRole("USER");
        when(userMapper.selectById(7L)).thenReturn(user);

        UserVO result = userService.getCurrentUser(7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.username()).isEqualTo("analyst_07");
        assertThat(result.nickname()).isEqualTo("分析员七号");
        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    void shouldRejectMissingCurrentUser() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getCurrentUser(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户不存在")
                .extracting(exception -> ((BusinessException) exception).getResultCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }
}
