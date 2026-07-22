package com.aianalyst.config;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RetriableException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCircuitBreakerFailurePredicateTest {

    private final ModelCircuitBreakerFailurePredicate predicate =
            new ModelCircuitBreakerFailurePredicate();
    private final ModelCircuitBreakerIgnorePredicate ignorePredicate =
            new ModelCircuitBreakerIgnorePredicate();

    @Test
    void shouldRecordTimeoutNetworkRetryableAndProviderFailures() {
        assertThat(predicate.test(new TimeoutException("timeout"))).isTrue();
        assertThat(predicate.test(new CompletionException(new IOException("network")))).isTrue();
        assertThat(predicate.test(new RetriableException("retryable"))).isTrue();
        assertThat(predicate.test(new HttpException(429, "rate limited"))).isTrue();
        assertThat(predicate.test(new HttpException(503, "unavailable"))).isTrue();
        assertThat(ignorePredicate.test(new TimeoutException("timeout"))).isFalse();
    }

    @Test
    void shouldIgnoreLocalBusinessValidationAndExecutorSaturation() {
        assertThat(predicate.test(new BusinessException(ResultCode.SQL_AUDIT_FAILED))).isFalse();
        assertThat(predicate.test(new IllegalArgumentException("invalid prompt"))).isFalse();
        assertThat(predicate.test(new HttpException(400, "bad request"))).isFalse();
        assertThat(predicate.test(new TaskRejectedException(
                new ThreadPoolExecutor.AbortPolicy().toString()))).isFalse();
        assertThat(ignorePredicate.test(
                new BusinessException(ResultCode.SQL_AUDIT_FAILED))).isTrue();
        assertThat(ignorePredicate.test(
                new TaskRejectedException("executor saturated"))).isTrue();
    }
}
