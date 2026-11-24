package com.heuristiq.uam.readiness.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.uam.application.ApplicationConstants;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.readiness.application.port.GovernanceCaller;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityAudit;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.uam.readiness.domain.ApplicationReadiness;
import com.heuristiq.uam.readiness.domain.ReadinessProfiles;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;
import com.heuristiq.uam.readiness.domain.ReadinessSeverityConstants;
import com.heuristiq.uam.readiness.domain.ReadinessSeverityException;
import com.heuristiq.uam.readiness.domain.ReadinessSeverityRules;
import com.heuristiq.uam.readiness.domain.ReadinessStepDefinition;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort;

/**
 * Applies {@link ReadinessSeverityRules} to the session organization's overrides of one application: reads them, computes the
 * new set, stores it (only when it changed), writes a domain audit event with the before and after severity of every step that
 * changed, evicts the cached readiness and returns the fresh result. The organization is always the session's and the
 * application must be linked to it, exactly like the readiness GET. Two admins changing the same application at the same
 * moment: the last write wins (the rows are replaced as one set).
 */
public class ReadinessSeverityService implements ReadinessSeverityUseCase {

    private final UamReadinessFactsPort facts;
    private final ReadinessSeverityOverrideRepository overrides;
    private final ApplicationReadinessUseCase readiness;
    private final ReadinessSeverityAudit audit;
    private final GovernanceCaller caller;
    private final Clock clock;

    public ReadinessSeverityService(UamReadinessFactsPort facts, ReadinessSeverityOverrideRepository overrides,
                                    ApplicationReadinessUseCase readiness, ReadinessSeverityAudit audit,
                                    GovernanceCaller caller, Clock clock) {
        this.facts = facts;
        this.overrides = overrides;
        this.readiness = readiness;
        this.audit = audit;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    public ApplicationReadiness change(String applicationCode, String stepId, String severity) {
        ReadinessSeverity target = parse(severity);
        return apply(applicationCode, ReadinessSeverityConstants.AUDIT_REMARK_CHANGED,
                (defaults, current) -> ReadinessSeverityRules.change(defaults, current, stepId, target));
    }

    @Override
    public ApplicationReadiness reset(String applicationCode, String stepId) {
        return apply(applicationCode, ReadinessSeverityConstants.AUDIT_REMARK_RESET,
                (defaults, current) -> ReadinessSeverityRules.reset(defaults, current, stepId));
    }

    @Override
    public ApplicationReadiness resetAll(String applicationCode) {
        return apply(applicationCode, ReadinessSeverityConstants.AUDIT_REMARK_RESET_ALL, (defaults, current) -> Map.of());
    }

    /** Computes the next set of overrides from the current one. */
    @FunctionalInterface
    private interface Transition {
        Map<String, ReadinessSeverity> next(List<ReadinessStepDefinition> defaults, Map<String, ReadinessSeverity> current);
    }

    private ApplicationReadiness apply(String applicationCode, String remarks, Transition transition) {
        String tenantId = facts.sessionOrganizationId();
        String code = applicationCode == null ? "" : applicationCode.trim().toUpperCase(Locale.ROOT);
        List<ReadinessStepDefinition> defaults = ReadinessProfiles.forApplication(code)
                .filter(profile -> facts.linkedApplicationCodes(tenantId).contains(code))
                .orElseThrow(() -> UamException.notFound(ApplicationConstants.NOT_FOUND));
        Map<String, ReadinessSeverity> current = ReadinessSeverityRules.normalize(defaults, overrides.find(tenantId, code));
        Map<String, ReadinessSeverity> next = transition.next(defaults, current);
        if (!next.equals(current)) {
            String userId = caller.userId();
            overrides.replace(tenantId, code, next, userId, clock.instant());
            readiness.invalidate(tenantId, code);
            recordAudit(tenantId, code, userId, remarks, defaults, current, next);
        }
        return readiness.readiness(code, false);
    }

    private void recordAudit(String tenantId, String code, String userId, String remarks, List<ReadinessStepDefinition> defaults,
                             Map<String, ReadinessSeverity> before, Map<String, ReadinessSeverity> after) {
        Map<String, ReadinessSeverity> was = effectiveById(defaults, before);
        Map<String, ReadinessSeverity> now = effectiveById(defaults, after);
        Map<String, Object> beforeState = new LinkedHashMap<>();
        Map<String, Object> afterState = new LinkedHashMap<>();
        now.forEach((id, severity) -> {
            if (was.get(id) != severity) {
                beforeState.put(id, was.get(id).name());
                afterState.put(id, severity.name());
            }
        });
        afterState.put("applicationCode", code);
        afterState.put("changedBy", userId);
        audit.record(tenantId, userId, tenantId + ":" + code, beforeState, afterState, remarks);
    }

    private static Map<String, ReadinessSeverity> effectiveById(List<ReadinessStepDefinition> defaults,
                                                                Map<String, ReadinessSeverity> overrides) {
        Map<String, ReadinessSeverity> byId = new LinkedHashMap<>();
        ReadinessSeverityRules.effective(defaults, overrides).forEach(definition -> byId.put(definition.id(), definition.severity()));
        return byId;
    }

    private static ReadinessSeverity parse(String severity) {
        if (severity == null) {
            throw ReadinessSeverityException.invalid();
        }
        try {
            return ReadinessSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReadinessSeverityException.invalid();
        }
    }
}
