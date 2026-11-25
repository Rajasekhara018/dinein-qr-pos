package com.heuristiq.identity.readiness.application;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heuristiq.identity.organization.subscription.GoLiveGuard;
import com.heuristiq.identity.readiness.application.port.GovernanceAudit;
import com.heuristiq.identity.readiness.application.port.GovernanceCaller;
import com.heuristiq.identity.readiness.config.GovernanceProperties;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.GoLiveBlocker;
import com.heuristiq.identity.readiness.domain.GoLiveRefusedException;
import com.heuristiq.identity.readiness.domain.GoLiveRules;
import com.heuristiq.identity.readiness.domain.GovernanceConstants;
import com.heuristiq.identity.readiness.domain.ReadinessProfiles;

/**
 * Refuses to switch an application on for an organization while a BLOCK step fails or is UNKNOWN (typed 409 listing the
 * blocking steps). WARN steps never block. Evaluation is fresh (the cache is bypassed) for the organization named by the
 * caller of the guard. An application with no readiness profile is not guarded. Switched off by
 * {@code railcab.uam.readiness.governance.guard-enabled=false}.
 * <p>
 * If the evaluation itself fails unexpectedly the error is passed on and the request fails (the guard fails closed).
 */
public class GoLiveGuardService implements GoLiveGuard {

    private static final Logger log = LoggerFactory.getLogger(GoLiveGuardService.class);

    private final ApplicationReadinessUseCase readiness;
    private final ReadinessAlertService alerts;
    private final GovernanceAudit audit;
    private final GovernanceCaller caller;
    private final GovernanceProperties properties;

    public GoLiveGuardService(ApplicationReadinessUseCase readiness, ReadinessAlertService alerts, GovernanceAudit audit,
                              GovernanceCaller caller, GovernanceProperties properties) {
        this.readiness = readiness;
        this.alerts = alerts;
        this.audit = audit;
        this.caller = caller;
        this.properties = properties;
    }

    @Override
    public void requireReady(String tenantId, String applicationCode) {
        if (!properties.guardEnabled() || ReadinessProfiles.forApplication(applicationCode).isEmpty()) {
            return;
        }
        ApplicationReadiness result = readiness.evaluateFor(tenantId, applicationCode);
        List<GoLiveBlocker> blockers = GoLiveRules.blockers(result);
        if (blockers.isEmpty()) {
            return;
        }
        String code = result.applicationCode();
        String attemptedBy = caller.userId();
        log.info("readiness.golive.refused tenant={} application={} blockers={}", tenantId, code, GoLiveRules.stepIds(blockers));
        try {
            audit.record(tenantId, attemptedBy, tenantId + ":" + code, false, Map.of(),
                    Map.of("applicationCode", code, "blockingSteps", GoLiveRules.stepIds(blockers)),
                    GovernanceConstants.AUDIT_REMARK_REFUSED);
            alerts.refused(tenantId, code, result.applicationName(), blockers, attemptedBy);
        } catch (RuntimeException e) {
            log.warn("readiness.golive.refused.followup-failed tenant={} application={} reason={}", tenantId, code, e.toString());
        }
        throw new GoLiveRefusedException(code, blockers);
    }
}
