package com.aianalyst.service;

import java.util.concurrent.CompletableFuture;

/** Stage-specific resilience boundary around model I/O. */
public interface ModelResilienceGateway {

    CompletableFuture<String> planContext(String prompt);

    CompletableFuture<String> generateSql(String prompt);

    CompletableFuture<String> compressContext(String prompt);

    CompletableFuture<String> analyzeResult(String prompt);
}
