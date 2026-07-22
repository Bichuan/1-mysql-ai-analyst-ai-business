package com.aianalyst.service.impl;

import com.aianalyst.service.AsyncModelInvoker;
import com.aianalyst.service.DeepSeekChatService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncModelInvokerImpl implements AsyncModelInvoker {

    private final DeepSeekChatService deepSeekChatService;

    public AsyncModelInvokerImpl(DeepSeekChatService deepSeekChatService) {
        this.deepSeekChatService = deepSeekChatService;
    }

    @Async("llmCoreExecutor")
    @Override
    public CompletableFuture<String> invokeCore(String prompt) {
        return CompletableFuture.completedFuture(deepSeekChatService.generate(prompt));
    }

    @Async("llmAnalysisExecutor")
    @Override
    public CompletableFuture<String> invokeAnalysis(String prompt) {
        return CompletableFuture.completedFuture(deepSeekChatService.generate(prompt));
    }
}
