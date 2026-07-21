package com.aianalyst.service.impl;

import com.aianalyst.common.BusinessException;
import com.aianalyst.common.ResultCode;
import com.aianalyst.config.DeepSeekProperties;
import com.aianalyst.dto.TokenBudgetAssessment;
import com.aianalyst.service.TokenBudgetService;
import com.aianalyst.service.TokenEstimator;
import org.springframework.stereotype.Service;

/** Computes and enforces the hard model-window budget before every remote model call. */
@Service
public class TokenBudgetServiceImpl implements TokenBudgetService {

    private final DeepSeekProperties properties;
    private final TokenEstimator tokenEstimator;

    public TokenBudgetServiceImpl(DeepSeekProperties properties, TokenEstimator tokenEstimator) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public TokenBudgetAssessment assess(String prompt) {
        int contextWindow = properties.getContextWindowTokens();
        int threshold = (int) Math.floor(contextWindow * properties.getContextUsageLimit());
        int reservedOutput = properties.getMaxTokens();
        int safetyMargin = properties.getTokenSafetyMargin();
        if (threshold <= reservedOutput + safetyMargin) {
            throw new IllegalStateException(
                    "DeepSeek token budget is invalid: output reserve and margin consume the threshold");
        }
        int estimatedPrompt = tokenEstimator.estimate(prompt);
        boolean exceeds = (long) estimatedPrompt + reservedOutput + safetyMargin > threshold;
        return new TokenBudgetAssessment(
                estimatedPrompt,
                reservedOutput,
                safetyMargin,
                threshold,
                contextWindow,
                exceeds);
    }

    @Override
    public TokenBudgetAssessment requireWithinBudget(String prompt) {
        TokenBudgetAssessment assessment = assess(prompt);
        if (assessment.exceedsLimit()) {
            throw new BusinessException(
                    ResultCode.CONTEXT_WINDOW_EXCEEDED,
                    "模型上下文预计占用 %.1f%%，超过 %.0f%% 安全阈值"
                            .formatted(
                                    assessment.estimatedWindowUsageRatio() * 100.0D,
                                    properties.getContextUsageLimit() * 100.0D));
        }
        return assessment;
    }
}
