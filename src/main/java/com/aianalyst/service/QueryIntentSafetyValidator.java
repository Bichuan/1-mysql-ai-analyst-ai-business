package com.aianalyst.service;

/**
 * 校验用户问题是否包含写数据意图。
 * AI 数据助手的能力边界是只读分析，不能把删除、修改等命令悄悄降级改写为 SELECT。
 */
public interface QueryIntentSafetyValidator {

    void validateReadOnlyIntent(String question);
}
