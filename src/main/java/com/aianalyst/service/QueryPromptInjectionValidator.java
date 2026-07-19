package com.aianalyst.service;

/**
 * 在调用大模型前识别 Prompt 泄露、角色劫持和指令覆盖等攻击。
 */
public interface QueryPromptInjectionValidator {

    void validate(String question);
}
