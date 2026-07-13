package com.aianalyst.service;

/** Thin application boundary around the configured DeepSeek chat model. */
public interface DeepSeekChatService {

    String generate(String prompt);
}
