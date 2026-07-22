package com.aianalyst.service;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ModelCallException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** Converts asynchronous infrastructure failures to one stable application exception. */
public final class ModelCallAwaiter {

    private ModelCallAwaiter() {
    }

    public static String await(ModelCallType callType,
                               Supplier<CompletableFuture<String>> operation) {
        try {
            CompletableFuture<String> future = operation.get();
            if (future == null) {
                throw new IllegalStateException("model gateway returned a null future");
            }
            return future.join();
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new ModelCallException(callType, cause);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
