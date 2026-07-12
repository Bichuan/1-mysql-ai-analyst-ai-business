package com.aianalyst.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void shouldCreateSuccessResponse() {
        Result<String> result = Result.success("ok");

        assertThat(result.code()).isEqualTo(ResultCode.SUCCESS.getCode());
        assertThat(result.message()).isEqualTo("success");
        assertThat(result.data()).isEqualTo("ok");
    }

    @Test
    void shouldCreateErrorResponse() {
        Result<Void> result = Result.error(ResultCode.PARAM_ERROR);

        assertThat(result.code()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(result.message()).isEqualTo(ResultCode.PARAM_ERROR.getMessage());
        assertThat(result.data()).isNull();
    }
}
