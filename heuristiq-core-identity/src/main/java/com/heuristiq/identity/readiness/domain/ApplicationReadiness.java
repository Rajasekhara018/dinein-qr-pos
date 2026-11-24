package com.heuristiq.uam.readiness.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Readiness of one application for one organization: the ordered guide and its summary.
 *
 * @param applicationCode catalogue code
 * @param applicationName display name
 * @param evaluatedAt     when it was computed (a cached result keeps its original time)
 * @param status          overall status (rules in {@link ReadinessEvaluator})
 * @param requiredTotal   BLOCK steps that are not NOT_APPLICABLE
 * @param requiredPassed  of those, the ones that are PASS or WARN
 * @param warningCount    WARN-severity steps not passed, plus BLOCK steps a service reported as WARN
 * @param nextStepId      the step to work on next, or null when there is none
 * @param steps           every step, in phase then order
 */
public record ApplicationReadiness(String applicationCode, String applicationName, Instant evaluatedAt,
                                   OverallReadinessStatus status, int requiredTotal, int requiredPassed, int warningCount,
                                   String nextStepId, List<ReadinessStep> steps) {

    public ApplicationReadiness {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /** The step named by {@link #nextStepId}, if any. */
    public Optional<ReadinessStep> nextStep() {
        return nextStepId == null ? Optional.empty() : steps.stream().filter(step -> step.id().equals(nextStepId)).findFirst();
    }
}
