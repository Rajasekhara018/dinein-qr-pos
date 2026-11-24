package com.heuristiq.uam.readiness.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import com.heuristiq.uam.application.ApplicationConstants;
import com.heuristiq.uam.application.SessionOrganizationResolver;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase.GoLiveOverride;
import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.readiness.application.port.GovernanceAudit;
import com.heuristiq.uam.readiness.application.port.GovernanceCaller;
import com.heuristiq.uam.readiness.application.port.OwnerCandidate;
import com.heuristiq.uam.readiness.application.port.OwnerCandidateDirectory;
import com.heuristiq.uam.readiness.config.GovernanceProperties;
import com.heuristiq.uam.readiness.domain.ApplicationReadiness;
import com.heuristiq.uam.readiness.domain.GoLiveBlocker;
import com.heuristiq.uam.readiness.domain.GoLiveRules;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;
import com.heuristiq.uam.readiness.domain.ReadinessProfiles;

/**
 * The SUPER_ADMIN go-live override, the owner team of an application, and the governance state shown to the UI. The
 * organization is always the session's. Only a SUPER_ADMIN may override and must give a reason of the configured length;
 * the override is audited (before/after, who, application, organization, blocking steps, reason), recorded on the link and
 * alerted to the super admins and the organization's owner team.
 */
public class ReadinessGovernanceService {

    private static final String LIST_SEPARATOR = ",";

    private final SessionOrganizationResolver session;
    private final TenantApplicationServiceUseCase links;
    private final ApplicationReadinessUseCase readiness;
    private final OwnerCandidateDirectory candidates;
    private final ReadinessAlertService alerts;
    private final GovernanceAudit audit;
    private final GovernanceCaller caller;
    private final GovernanceProperties properties;
    private final Clock clock;

    public ReadinessGovernanceService(SessionOrganizationResolver session, TenantApplicationServiceUseCase links,
                                      ApplicationReadinessUseCase readiness, OwnerCandidateDirectory candidates,
                                      ReadinessAlertService alerts, GovernanceAudit audit, GovernanceCaller caller,
                                      GovernanceProperties properties, Clock clock) {
        this.session = session;
        this.links = links;
        this.readiness = readiness;
        this.candidates = candidates;
        this.alerts = alerts;
        this.audit = audit;
        this.caller = caller;
        this.properties = properties;
        this.clock = clock;
    }

    /** What the UI shows about governance of one application. */
    public record State(String applicationCode, List<String> ownerTeamUserIds, OverrideInfo override, Alerts alerts, boolean guardEnabled) {
    }

    /** The last override recorded on the link. */
    public record OverrideInfo(String by, LocalDateTime at, String reason, List<String> blockingSteps) {
    }

    /** When the last alerts were sent (fingerprints are not exposed). */
    public record Alerts(LocalDateTime lastBlockedAt, LocalDateTime lastRefusedAt) {
    }

    /**
     * Switches the application on for the session organization although blocking steps fail.
     *
     * @throws UamException 403 for anyone but a SUPER_ADMIN; 400 for a reason outside the length limits or when nothing blocks;
     *                      404 when the organization does not use the application
     */
    public TenantApplication override(String applicationCode, String reason) {
        if (!caller.isSuperAdmin()) {
            throw new UamException(GovernanceConstants.OVERRIDE_FORBIDDEN_MESSAGE, HttpStatus.FORBIDDEN);
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < properties.overrideReasonMin() || trimmed.length() > properties.overrideReasonMax()) {
            throw new UamException(String.format(GovernanceConstants.OVERRIDE_REASON_INVALID_MESSAGE,
                    properties.overrideReasonMin(), properties.overrideReasonMax()));
        }
        String tenantId = session.sessionOrganizationId();
        String code = normalize(applicationCode);
        TenantApplication before = requireLink(tenantId, code);
        ApplicationReadiness result = readiness.evaluateFor(tenantId, code);
        List<GoLiveBlocker> blockers = GoLiveRules.blockers(result);
        if (blockers.isEmpty()) {
            throw new UamException(GovernanceConstants.NOTHING_TO_OVERRIDE_MESSAGE);
        }
        String userId = caller.userId();
        String steps = GoLiveRules.stepIds(blockers);
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("status", String.valueOf(before.getStatus()));
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("status", TenantApplicationStatus.ACTIVE.name());
        afterState.put("applicationCode", code);
        afterState.put("blockingSteps", steps);
        afterState.put("reason", trimmed);
        afterState.put("overriddenBy", userId);
        TenantApplication saved = links.activateWithOverride(tenantId, code,
                new GoLiveOverride(userId, LocalDateTime.now(clock), trimmed, steps), userId);
        audit.record(tenantId, userId, tenantId + ":" + code, true, beforeState, afterState, GovernanceConstants.AUDIT_REMARK_OVERRIDE);
        alerts.overridden(tenantId, code, result.applicationName(), blockers, userId, trimmed);
        return saved;
    }

    /**
     * Replaces the owner team of the session organization's link. Every id must be a department head of the organization
     * (the same list {@link #ownerCandidates()} returns).
     */
    public TenantApplication updateOwnerTeam(String applicationCode, List<String> userIds) {
        String tenantId = session.sessionOrganizationId();
        String code = normalize(applicationCode);
        Set<String> wanted = new LinkedHashSet<>();
        if (userIds != null) {
            userIds.stream().filter(StringUtils::hasText).map(String::trim).forEach(wanted::add);
        }
        if (wanted.size() > properties.ownerTeamMax()) {
            throw new UamException(String.format(GovernanceConstants.OWNER_TEAM_TOO_LARGE_MESSAGE, properties.ownerTeamMax()));
        }
        if (!wanted.isEmpty()) {
            Set<String> eligible = new LinkedHashSet<>();
            candidates.departmentHeads(tenantId).forEach(candidate -> eligible.add(candidate.userId()));
            List<String> rejected = wanted.stream().filter(id -> !eligible.contains(id)).toList();
            if (!rejected.isEmpty()) {
                throw new UamException(GovernanceConstants.OWNER_NOT_ELIGIBLE_MESSAGE + String.join(LIST_SEPARATOR, rejected));
            }
        }
        requireLink(tenantId, code);
        return links.updateOwnerTeam(tenantId, code, List.copyOf(wanted), caller.userId());
    }

    /** Department heads of the session organization who may be chosen as owners. */
    public List<OwnerCandidate> ownerCandidates() {
        return candidates.departmentHeads(session.sessionOrganizationId());
    }

    /** Owner team, last override and alert times of the session organization's link; 404 when it has none. */
    public State state(String applicationCode) {
        String tenantId = session.sessionOrganizationId();
        String code = normalize(applicationCode);
        TenantApplication link = requireLink(tenantId, code);
        OverrideInfo override = link.getGoLiveOverrideAt() == null ? null : new OverrideInfo(link.getGoLiveOverrideBy(),
                link.getGoLiveOverrideAt(), link.getGoLiveOverrideReason(), steps(link.getGoLiveOverrideSteps()));
        return new State(code, link.getOwnerTeamUserIds() == null ? List.of() : link.getOwnerTeamUserIds(), override,
                new Alerts(link.getLastBlockedAlertAt(), link.getLastRefusedAlertAt()), properties.guardEnabled());
    }

    private TenantApplication requireLink(String tenantId, String code) {
        return links.find(tenantId, code).filter(link -> link.getStatus() != TenantApplicationStatus.CANCELLED)
                .orElseThrow(() -> UamException.notFound(ApplicationConstants.NOT_FOUND));
    }

    private static List<String> steps(String stored) {
        return StringUtils.hasText(stored) ? List.of(stored.split(LIST_SEPARATOR)) : List.of();
    }

    private static String normalize(String applicationCode) {
        String code = applicationCode == null ? "" : applicationCode.trim().toUpperCase(Locale.ROOT);
        if (ReadinessProfiles.forApplication(code).isEmpty()) {
            throw UamException.notFound(ApplicationConstants.NOT_FOUND);
        }
        return code;
    }
}
