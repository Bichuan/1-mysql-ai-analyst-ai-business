package com.aianalyst.service;

/** Business stage of a model invocation, used for routing and diagnostics without logging prompts. */
public enum ModelCallType {
    CONTEXT_PLANNING,
    TEXT_TO_SQL,
    CONTEXT_COMPRESSION,
    RESULT_ANALYSIS
}
