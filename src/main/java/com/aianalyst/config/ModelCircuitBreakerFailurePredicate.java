package com.aianalyst.config;

import com.aianalyst.common.BusinessException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.core.task.TaskRejectedException;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/** Counts only upstream model availability failures; local validation and saturation stay isolated. */
public class ModelCircuitBreakerFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof BusinessException
                || cause instanceof CallNotPermittedException
                || cause instanceof TaskRejectedException
                || cause instanceof RejectedExecutionException
                || cause instanceof IllegalArgumentException) {
            return false;
        }
        if (cause instanceof TimeoutException
                || cause instanceof dev.langchain4j.exception.TimeoutException
                || cause instanceof RetriableException
                || cause instanceof AuthenticationException
                || cause instanceof UnresolvedModelServerException
                || cause instanceof IOException) {
            return true;
        }
        if (cause instanceof HttpException httpException) {
            int statusCode = httpException.statusCode();
            return statusCode == 401 || statusCode == 403
                    || statusCode == 408 || statusCode == 429 || statusCode >= 500;
        }
        return false;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
