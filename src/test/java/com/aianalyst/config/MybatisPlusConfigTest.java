package com.aianalyst.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void shouldCreatePaginationInterceptorWithCompatibleJsqlParser() {
        // 直接实例化可以尽早发现 MyBatis Plus 与 JSqlParser 的二进制版本冲突，避免到应用启动时才失败。
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor).isNotNull();
        assertThat(interceptor.getInterceptors()).hasSize(1);
    }
}
