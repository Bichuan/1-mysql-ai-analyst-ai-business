package com.aianalyst.service.impl;

import com.aianalyst.dto.ConversationTurnSnapshot;
import com.aianalyst.service.TokenEstimator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deliberately conservative approximation: two tokens per non-ASCII symbol and roughly four
 * ASCII word characters per token. It is a safety estimate, not billing-grade token accounting.
 */
@Component
public class ConservativeTokenEstimator implements TokenEstimator {

    private static final int CONTEXT_ENVELOPE_TOKENS = 8;
    private static final int TURN_ENVELOPE_TOKENS = 12;

    @Override
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long tokens = 0L;
        int asciiRun = 0;
        int[] codePoints = text.codePoints().toArray();
        for (int codePoint : codePoints) {
            if (isAsciiWordCharacter(codePoint)) {
                asciiRun++;
                continue;
            }
            tokens += asciiTokens(asciiRun);
            asciiRun = 0;
            if (!Character.isWhitespace(codePoint)) {
                tokens += codePoint < 128 ? 1L : 2L;
            }
        }
        tokens += asciiTokens(asciiRun);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, tokens));
    }

    @Override
    public int estimateConversationContext(String rollingSummary,
                                           String structuredState,
                                           List<ConversationTurnSnapshot> recentTurns) {
        boolean hasTurns = recentTurns != null && !recentTurns.isEmpty();
        if ((rollingSummary == null || rollingSummary.isBlank())
                && (structuredState == null || structuredState.isBlank())
                && !hasTurns) {
            return 0;
        }
        long tokens = CONTEXT_ENVELOPE_TOKENS
                + estimate(rollingSummary)
                + estimate(structuredState);
        if (recentTurns != null) {
            for (ConversationTurnSnapshot turn : recentTurns) {
                tokens += estimateTurn(
                        turn.originalQuestion(), turn.standaloneQuestion(), turn.answerSummary());
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, tokens);
    }

    @Override
    public int estimateTurn(String originalQuestion,
                            String standaloneQuestion,
                            String answerSummary) {
        long tokens = TURN_ENVELOPE_TOKENS
                + estimate(originalQuestion)
                + estimate(standaloneQuestion)
                + estimate(answerSummary);
        return (int) Math.min(Integer.MAX_VALUE, tokens);
    }

    private boolean isAsciiWordCharacter(int codePoint) {
        return codePoint < 128 && Character.isLetterOrDigit(codePoint);
    }

    private long asciiTokens(int characterCount) {
        return characterCount == 0 ? 0L : (characterCount + 3L) / 4L;
    }
}
