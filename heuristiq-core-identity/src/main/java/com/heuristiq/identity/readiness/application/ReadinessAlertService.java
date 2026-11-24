package com.heuristiq.uam.readiness.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase.AlertKind;
import com.heuristiq.uam.readiness.application.port.AlertRecipient;
import com.heuristiq.uam.readiness.application.port.AlertRecipientDirectory;
import com.heuristiq.uam.readiness.application.port.ReadinessAlertSender;
import com.heuristiq.uam.readiness.config.GovernanceProperties;
import com.heuristiq.uam.readiness.domain.GoLiveBlocker;
import com.heuristiq.uam.readiness.domain.GoLiveRules;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;

/**
 * Alerts about applications that are blocked or were forced live. Recipients are the platform's SUPER_ADMINs plus the owner
 * team of THIS organization's link to the application (never another organization's owners). Delivery is best effort:
 * UAM has no database outbox, so a failure is logged and never fails the caller. To compensate, the throttle state is only
 * saved after at least one message was accepted by NMS, so a total failure is retried by the next re-check.
 * <p>
 * Throttle: the same organization + application + blocking set is not alerted again within the configured interval; a
 * different blocking set alerts at once. The state is kept on the organization's link, so it survives restarts and is shared
 * by all instances. Override alerts are never throttled (each override is a distinct decision).
 */
public class ReadinessAlertService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessAlertService.class);
    private static final String STEP_SEPARATOR = ", ";

    private final TenantApplicationServiceUseCase links;
    private final AlertRecipientDirectory directory;
    private final ReadinessAlertSender sender;
    private final GovernanceProperties properties;
    private final Clock clock;

    public ReadinessAlertService(TenantApplicationServiceUseCase links, AlertRecipientDirectory directory,
                                 ReadinessAlertSender sender, GovernanceProperties properties, Clock clock) {
        this.links = links;
        this.directory = directory;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    /** The scheduled re-check found blocking steps on an ACTIVE link. Returns true when an alert was sent. */
    public boolean blocked(String tenantId, String applicationCode, String applicationName, List<GoLiveBlocker> blockers) {
        Map<String, String> variables = variables(applicationName, blockers);
        return throttled(tenantId, applicationCode, AlertKind.BLOCKED, GovernanceConstants.EVENT_READINESS_BLOCKED, blockers,
                variables, null);
    }

    /** Someone tried to switch the application on and was refused. Returns true when an alert was sent. */
    public boolean refused(String tenantId, String applicationCode, String applicationName, List<GoLiveBlocker> blockers,
                           String attemptedBy) {
        Map<String, String> variables = variables(applicationName, blockers);
        variables.put(GovernanceConstants.VAR_ATTEMPTED_BY, orUnknown(attemptedBy));
        return throttled(tenantId, applicationCode, AlertKind.REFUSED, GovernanceConstants.EVENT_GO_LIVE_REFUSED, blockers,
                variables, attemptedBy);
    }

    /** A SUPER_ADMIN forced the application live. Never throttled. Returns true when an alert was sent. */
    public boolean overridden(String tenantId, String applicationCode, String applicationName, List<GoLiveBlocker> blockers,
                              String overriddenBy, String reason) {
        if (!properties.alertsEnabled()) {
            return false;
        }
        Map<String, String> variables = variables(applicationName, blockers);
        variables.put(GovernanceConstants.VAR_OVERRIDDEN_BY, orUnknown(overriddenBy));
        variables.put(GovernanceConstants.VAR_REASON, reason == null ? "" : reason);
        return deliver(tenantId, applicationCode, GovernanceConstants.EVENT_GO_LIVE_OVERRIDDEN, variables, overriddenBy);
    }

    /** The blocking set is gone: forget the throttle state so the next failure alerts at once. */
    public void cleared(String tenantId, String applicationCode, TenantApplication link) {
        if (link != null && link.getLastBlockedAlertFingerprint() != null) {
            links.recordAlert(tenantId, applicationCode, AlertKind.BLOCKED, null, null);
        }
    }

    private boolean throttled(String tenantId, String applicationCode, AlertKind kind, String event,
                              List<GoLiveBlocker> blockers, Map<String, String> variables, String triggeredBy) {
        if (!properties.alertsEnabled() || blockers.isEmpty()) {
            return false;
        }
        TenantApplication link = links.find(tenantId, applicationCode).orElse(null);
        String fingerprint = GoLiveRules.fingerprint(blockers);
        LocalDateTime now = LocalDateTime.now(clock);
        if (link != null && !due(kind, link, fingerprint, now)) {
            return false;
        }
        boolean sent = deliver(tenantId, applicationCode, event, variables, triggeredBy);
        if (sent) {
            links.recordAlert(tenantId, applicationCode, kind, now, fingerprint);
        }
        return sent;
    }

    private boolean due(AlertKind kind, TenantApplication link, String fingerprint, LocalDateTime now) {
        LocalDateTime lastAt = kind == AlertKind.BLOCKED ? link.getLastBlockedAlertAt() : link.getLastRefusedAlertAt();
        String lastFingerprint = kind == AlertKind.BLOCKED ? link.getLastBlockedAlertFingerprint() : link.getLastRefusedAlertFingerprint();
        if (lastAt == null || lastFingerprint == null || !lastFingerprint.equals(fingerprint)) {
            return true;
        }
        return !now.isBefore(lastAt.plus(properties.alertInterval()));
    }

    private boolean deliver(String tenantId, String applicationCode, String event, Map<String, String> variables, String triggeredBy) {
        try {
            List<AlertRecipient> recipients = recipients(tenantId, applicationCode);
            if (recipients.isEmpty()) {
                log.warn("readiness.alert.no-recipients tenant={} application={} event={}", tenantId, applicationCode, event);
                return false;
            }
            String alertId = UUID.randomUUID().toString();
            String entityId = tenantId + ":" + applicationCode;
            int accepted = 0;
            for (AlertRecipient recipient : recipients) {
                Map<String, String> perPerson = new LinkedHashMap<>(variables);
                perPerson.put(GovernanceConstants.VAR_RECIPIENT_NAME, recipient.name() == null ? "" : recipient.name());
                if (sender.send(event, recipient, tenantId, perPerson, entityId, triggeredBy, alertId)) {
                    accepted++;
                }
            }
            log.info("readiness.alert.sent tenant={} application={} event={} recipients={} accepted={}", tenantId, applicationCode,
                    event, recipients.size(), accepted);
            return accepted > 0;
        } catch (RuntimeException e) {
            // Best effort: an alert must never fail the guard, the override or the re-check.
            log.warn("readiness.alert.failed tenant={} application={} event={} reason={}", tenantId, applicationCode, event, e.toString());
            return false;
        }
    }

    /** SUPER_ADMINs plus this organization's owner team, one entry per account that has an email. */
    List<AlertRecipient> recipients(String tenantId, String applicationCode) {
        Map<String, AlertRecipient> byUser = new LinkedHashMap<>();
        directory.superAdmins(properties.maxAlertRecipients()).forEach(r -> byUser.putIfAbsent(r.userId(), r));
        links.find(tenantId, applicationCode).map(TenantApplication::getOwnerTeamUserIds).ifPresent(ids -> {
            if (!ids.isEmpty()) {
                directory.activeMembers(tenantId, ids).forEach(r -> byUser.putIfAbsent(r.userId(), r));
            }
        });
        List<AlertRecipient> result = new ArrayList<>();
        for (AlertRecipient recipient : byUser.values()) {
            if (recipient.email() != null && !recipient.email().isBlank() && result.size() < properties.maxAlertRecipients()) {
                result.add(recipient);
            }
        }
        return result;
    }

    private static Map<String, String> variables(String applicationName, List<GoLiveBlocker> blockers) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put(GovernanceConstants.VAR_APPLICATION_NAME, applicationName == null ? "" : applicationName);
        variables.put(GovernanceConstants.VAR_BLOCKING_STEPS, String.join(STEP_SEPARATOR, blockers.stream().map(GoLiveBlocker::stepId).sorted().toList()));
        return variables;
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
