package com.heuristiq.identity.readiness.application;

import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessStep;

/**
 * One line of the readiness summary of the session organization's applications.
 *
 * @param applicationCode catalogue code
 * @param applicationName display name
 * @param status          overall status
 * @param requiredTotal   BLOCK steps that apply
 * @param requiredPassed  of those, the passed ones
 * @param warningCount    warnings
 * @param nextStep        the step to work on next, or null
 */
public record ReadinessSummary(String applicationCode, String applicationName, OverallReadinessStatus status,
                               int requiredTotal, int requiredPassed, int warningCount, ReadinessStep nextStep) {

    /** The summary of a full result. */
    public static ReadinessSummary of(ApplicationReadiness readiness) {
        return new ReadinessSummary(readiness.applicationCode(), readiness.applicationName(), readiness.status(),
                readiness.requiredTotal(), readiness.requiredPassed(), readiness.warningCount(),
                readiness.nextStep().orElse(null));
    }
}
