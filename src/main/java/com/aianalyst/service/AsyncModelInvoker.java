package com.aianalyst.service;

import java.util.concurrent.CompletableFuture;

/** Executes blocking model I/O on bounded, stage-appropriate executors. */
public interface AsyncModelInvoker {

    CompletableFuture<String> invokeCore(String prompt);

    CompletableFuture<String> invokeAnalysis(String prompt);
}
