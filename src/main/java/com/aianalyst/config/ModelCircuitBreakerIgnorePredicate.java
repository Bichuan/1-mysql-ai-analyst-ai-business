package com.aianalyst.config;

import java.util.function.Predicate;

/** Excludes every non-provider failure from the circuit-breaker call window instead of marking it successful. */
public class ModelCircuitBreakerIgnorePredicate implements Predicate<Throwable> {

    private final ModelCircuitBreakerFailurePredicate failurePredicate =
            new ModelCircuitBreakerFailurePredicate();

    @Override
    public boolean test(Throwable throwable) {
        return !failurePredicate.test(throwable);
    }
}
