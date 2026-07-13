package com.aianalyst.service;

/**
 * 对自然语言问题执行确定性的业务规则校验。
 * 它不尝试替代大模型理解全部语义，只在调用模型前拦截明确、可判定的非法输入。
 */
public interface QuerySemanticValidator {

    void validate(String question);
}
