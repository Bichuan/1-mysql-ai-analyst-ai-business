package com.aianalyst.handler;

import com.aianalyst.common.ModelCallException;
import com.aianalyst.common.Result;
import com.aianalyst.common.ResultCode;
import com.aianalyst.service.ModelCallType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void shouldMapModelUnavailableToHttp503() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new ModelCallException(
                        ModelCallType.TEXT_TO_SQL,
                        new TimeoutException("provider timeout")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo(ResultCode.MODEL_SERVICE_UNAVAILABLE.getCode());
        assertThat(response.getBody().message())
                .isEqualTo(ResultCode.MODEL_SERVICE_UNAVAILABLE.getMessage());
    }
}
