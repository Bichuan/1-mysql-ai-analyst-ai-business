package com.aianalyst.service;

import com.aianalyst.dto.TokenBudgetAssessment;

/** Contract for assessing and enforcing the hard model-window budget. */
public interface TokenBudgetService {

    TokenBudgetAssessment assess(String prompt);

    TokenBudgetAssessment requireWithinBudget(String prompt);
}
