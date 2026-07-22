package com.aianalyst.service;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ModelCallException;
import com.aianalyst.common.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCallAwaiterTest {

    @Test
    void shouldReturnCompletedModelResponse() {
        String response = ModelCallAwaiter.await(
                ModelCallType.TEXT_TO_SQL,
                () -> CompletableFuture.completedFuture("SELECT 1"));

        assertThat(response).isEqualTo("SELECT 1");
    }

    @Test
    void shouldConvertAsyncInfrastructureFailureToStableModelException() {
        TimeoutException timeout = new TimeoutException("provider timed out");

        assertThatThrownBy(() -> ModelCallAwaiter.await(
                ModelCallType.CONTEXT_PLANNING,
                () -> CompletableFuture.failedFuture(timeout)))
                .isInstanceOf(ModelCallException.class)
                .satisfies(exception -> {
                    ModelCallException modelException = (ModelCallException) exception;
                    assertThat(modelException.getResultCode())
                            .isEqualTo(ResultCode.MODEL_SERVICE_UNAVAILABLE);
                    assertThat(modelException.getCallType())
                            .isEqualTo(ModelCallType.CONTEXT_PLANNING);
                    assertThat(modelException.getCause()).isSameAs(timeout);
                });
    }

    @Test
    void shouldAlsoConvertSynchronousGatewayRejection() {
        IllegalStateException rejection = new IllegalStateException("executor saturated");

        assertThatThrownBy(() -> ModelCallAwaiter.await(
                ModelCallType.RESULT_ANALYSIS,
                () -> {
                    throw rejection;
                }))
                .isInstanceOf(ModelCallException.class)
                .hasCause(rejection);
    }

    @Test
    void shouldPreserveLocalBusinessException() {
        BusinessException businessException = new BusinessException(
                ResultCode.CONTEXT_WINDOW_EXCEEDED);

        assertThatThrownBy(() -> ModelCallAwaiter.await(
                ModelCallType.CONTEXT_COMPRESSION,
                () -> CompletableFuture.failedFuture(businessException)))
                .isSameAs(businessException);
    }
}
