package com.aianalyst.service.impl;

import com.aianalyst.service.AsyncModelInvoker;
import com.aianalyst.service.ModelResilienceGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ModelResilienceGatewayImpl implements ModelResilienceGateway {

    private final AsyncModelInvoker asyncModelInvoker;

    public ModelResilienceGatewayImpl(AsyncModelInvoker asyncModelInvoker) {
        this.asyncModelInvoker = asyncModelInvoker;
    }

    @Override
    @CircuitBreaker(name = "llm-context-planning")
    @TimeLimiter(name = "llm-context-planning")
    public CompletableFuture<String> planContext(String prompt) {
        return asyncModelInvoker.invokeCore(prompt);
    }

    @Override
    @CircuitBreaker(name = "llm-text-to-sql")
    @TimeLimiter(name = "llm-text-to-sql")
    public CompletableFuture<String> generateSql(String prompt) {
        return asyncModelInvoker.invokeCore(prompt);
    }

    @Override
    @CircuitBreaker(name = "llm-context-compression")
    @TimeLimiter(name = "llm-context-compression")
    public CompletableFuture<String> compressContext(String prompt) {
        return asyncModelInvoker.invokeCore(prompt);
    }

    @Override
    @CircuitBreaker(name = "llm-result-analysis")
    @TimeLimiter(name = "llm-result-analysis")
    public CompletableFuture<String> analyzeResult(String prompt) {
        return asyncModelInvoker.invokeAnalysis(prompt);
    }
}
