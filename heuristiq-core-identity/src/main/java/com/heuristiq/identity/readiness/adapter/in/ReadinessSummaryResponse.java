package com.heuristiq.uam.readiness.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;

import com.heuristiq.uam.readiness.application.ReadinessSummary;

/**
 * Wire shape of one line of the readiness summary.
 *
 * @param applicationCode catalogue code
 * @param applicationName display name
 * @param status          READY, NEEDS_SETUP, BLOCKED or UNKNOWN
 * @param requiredTotal   BLOCK steps that apply
 * @param requiredPassed  of those, the passed ones
 * @param warningCount    warnings
 * @param nextStep        the step to work on next, or null
 */
@Schema(description = "Readiness summary of one application of the session organization.")
public record ReadinessSummaryResponse(String applicationCode, String applicationName, String status, int requiredTotal,
                                       int requiredPassed, int warningCount, NextStep nextStep) {

    /**
     * The step to work on next.
     *
     * @param id         check id
     * @param messageKey translation key
     * @param fixPath    Rider_Web route that fixes it, or null
     */
    @Schema(description = "The step to work on next.")
    public record NextStep(String id, String messageKey, String fixPath) {
    }

    /** Maps a summary to its wire shape. */
    public static ReadinessSummaryResponse from(ReadinessSummary summary) {
        NextStep next = summary.nextStep() == null ? null
                : new NextStep(summary.nextStep().id(), summary.nextStep().messageKey(), summary.nextStep().fixPath());
        return new ReadinessSummaryResponse(summary.applicationCode(), summary.applicationName(), summary.status().name(),
                summary.requiredTotal(), summary.requiredPassed(), summary.warningCount(), next);
    }
}
