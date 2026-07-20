package com.aianalyst.service;

import com.aianalyst.dto.ResolvedConversationQuestion;

/** Rewrites a contextual follow-up into a complete standalone business question. */
public interface ConversationQuestionResolver {

    ResolvedConversationQuestion resolve(Long userId, String conversationId, String currentQuestion);
}
