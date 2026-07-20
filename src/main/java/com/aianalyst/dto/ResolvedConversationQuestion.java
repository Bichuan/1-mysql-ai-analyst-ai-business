package com.aianalyst.dto;

/** Safe standalone question and state decision resolved from a multi-turn conversation. */
public record ResolvedConversationQuestion(
        String standaloneQuestion,
        boolean topicChanged,
        String structuredState,
        String rollingSummary,
        long summaryUntilTurn) {

    public static ResolvedConversationQuestion firstTurn(String question) {
        return new ResolvedConversationQuestion(question, false, null, null, 0L);
    }
}
