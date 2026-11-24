package com.heuristiq.uam.readiness.domain;

import java.util.List;
import java.util.Map;

/**
 * One step of the guided path as shown to the user: the profile entry plus the result.
 *
 * @param id         check id
 * @param phase      phase of the guided path
 * @param order      position inside the phase
 * @param service    slug of the reporting service ({@code uam}, {@code wallet}, ...)
 * @param severity   BLOCK or WARN
 * @param status     the result; UNKNOWN when the service did not answer or did not report the check
 * @param messageKey translation key owned by Rider_Web
 * @param params     string params of the message
 * @param fixPath    Rider_Web route that fixes it, or null
 * @param needs      ids of the steps that must be satisfied first
 * @param locked     true while one of {@code needs} is not satisfied (PASS, WARN or NOT_APPLICABLE)
 * @param defaultSeverity the severity in the code profile, before the organization's overrides
 * @param customized true when {@code severity} differs from {@code defaultSeverity}
 * @param severityLocked true when the severity can not be changed ({@link ReadinessSeverityRules#isLocked})
 */
public record ReadinessStep(String id, ReadinessPhase phase, int order, String service, ReadinessSeverity severity,
                            ReadinessStatus status, String messageKey, Map<String, String> params, String fixPath,
                            List<String> needs, boolean locked, ReadinessSeverity defaultSeverity,
                            boolean customized, boolean severityLocked) {

    public ReadinessStep {
        params = params == null ? Map.of() : Map.copyOf(params);
        needs = needs == null ? List.of() : List.copyOf(needs);
    }

    /** A step with the code-default severity (not customized). */
    public ReadinessStep(String id, ReadinessPhase phase, int order, String service, ReadinessSeverity severity,
                         ReadinessStatus status, String messageKey, Map<String, String> params, String fixPath,
                         List<String> needs, boolean locked) {
        this(id, phase, order, service, severity, status, messageKey, params, fixPath, needs, locked, severity, false,
                ReadinessSeverityRules.isLocked(id));
    }
}
