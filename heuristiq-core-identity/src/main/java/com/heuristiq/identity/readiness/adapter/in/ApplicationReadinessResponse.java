package com.heuristiq.uam.readiness.adapter.in;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import com.heuristiq.uam.readiness.domain.ApplicationReadiness;
import com.heuristiq.uam.readiness.domain.ReadinessStep;

/**
 * Wire shape of one application's readiness guide.
 *
 * @param applicationCode catalogue code
 * @param applicationName display name
 * @param evaluatedAt     ISO-8601 UTC instant of the evaluation
 * @param status          READY, NEEDS_SETUP, BLOCKED or UNKNOWN
 * @param requiredTotal   BLOCK steps that apply
 * @param requiredPassed  of those, the passed ones
 * @param warningCount    warnings
 * @param nextStepId      the step to work on next, or null
 * @param steps           every step in phase then order
 */
@Schema(description = "Readiness of one application for the session organization.")
public record ApplicationReadinessResponse(String applicationCode, String applicationName, String evaluatedAt, String status,
                                           int requiredTotal, int requiredPassed, int warningCount, String nextStepId,
                                           List<Step> steps) {

    /**
     * One step of the guide.
     *
     * @param id         check id
     * @param phase      ACCESS, SUPPORT_APPS, BUSINESS, CONNECTIVITY or GO_LIVE
     * @param order      position inside the phase
     * @param service    uam, wallet, onboarding, reports, dms or nms
     * @param severity   BLOCK or WARN
     * @param status     PASS, FAIL, WARN, UNKNOWN or NOT_APPLICABLE
     * @param messageKey translation key
     * @param params     string params of the message
     * @param fixPath    Rider_Web route that fixes it, or null
     * @param needs      ids of the steps that must be satisfied first
     * @param locked     true while a step in {@code needs} is not satisfied
     * @param defaultSeverity the severity in the code profile (BLOCK or WARN)
     * @param customized true when the organization changed the severity, i.e. {@code severity} differs from {@code defaultSeverity}
     * @param severityLocked true when the severity can not be changed (go-live review, application active/linked, connectivity)
     */
    @Schema(description = "One step of the readiness guide.")
    public record Step(String id, String phase, int order, String service, String severity, String status, String messageKey,
                       Map<String, String> params, String fixPath, List<String> needs, boolean locked, String defaultSeverity, boolean customized,
                       boolean severityLocked) {

        static Step from(ReadinessStep step) {
            return new Step(step.id(), step.phase().name(), step.order(), step.service(), step.severity().name(),
                    step.status().name(), step.messageKey(), step.params(), step.fixPath(), step.needs(), step.locked(),
                    step.defaultSeverity().name(), step.customized(), step.severityLocked());
        }
    }

    /** Maps a result to its wire shape. */
    public static ApplicationReadinessResponse from(ApplicationReadiness readiness) {
        return new ApplicationReadinessResponse(readiness.applicationCode(), readiness.applicationName(),
                readiness.evaluatedAt().toString(), readiness.status().name(), readiness.requiredTotal(),
                readiness.requiredPassed(), readiness.warningCount(), readiness.nextStepId(),
                readiness.steps().stream().map(Step::from).toList());
    }
}
