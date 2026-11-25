package com.heuristiq.identity.readiness.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.readiness.application.port.AlertRecipient;
import com.heuristiq.identity.readiness.application.port.AlertRecipientDirectory;
import com.heuristiq.identity.readiness.application.port.GovernanceAudit;
import com.heuristiq.identity.readiness.application.port.GovernanceCaller;
import com.heuristiq.identity.readiness.application.port.OwnerCandidate;
import com.heuristiq.identity.readiness.application.port.OwnerCandidateDirectory;
import com.heuristiq.identity.readiness.application.port.ReadinessAlertSender;
import com.heuristiq.identity.readiness.application.port.SchedulerLease;
import com.heuristiq.identity.readiness.config.GovernanceProperties;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessMessageKeys;
import com.heuristiq.identity.readiness.domain.ReadinessProfiles;
import com.heuristiq.identity.readiness.domain.ReadinessSeverity;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessStep;
import com.heuristiq.identity.readiness.domain.ReadinessStepDefinition;

/** In-memory stand-ins for the ports of the governance feature, shared by its tests. */
final class GovernanceFakes {

    static final String WALLET_APP = ApplicationCatalogue.CENTRALISED_WALLET_APP;
    static final String BEST_PURCHASE_APP = ApplicationCatalogue.BEST_PURCHASE_APP;
    static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private GovernanceFakes() {
    }

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /** Defaults, with the given overrides applied by the caller through the builder-like method below. */
    static GovernanceProperties properties() {
        return properties(true, true);
    }

    static GovernanceProperties properties(boolean guardEnabled, boolean alertsEnabled) {
        return new GovernanceProperties(guardEnabled, 10, 500, 3, Duration.ofHours(24), alertsEnabled, 50, true,
                Duration.ofHours(6), Duration.ofMinutes(5), 2, 500, Duration.ofMinutes(30));
    }

    /**
     * A readiness result for the application where every step is PASS except the ones named in {@code statuses}. The step
     * severity is the profile's own. Steps named but absent from the profile are ignored.
     */
    static ApplicationReadiness readiness(String code, Map<String, ReadinessStatus> statuses) {
        List<ReadinessStep> steps = new ArrayList<>();
        for (ReadinessStepDefinition definition : ReadinessProfiles.forApplication(code).orElseThrow()) {
            ReadinessStatus status = statuses.getOrDefault(definition.id(), ReadinessStatus.PASS);
            steps.add(new ReadinessStep(definition.id(), definition.phase(), definition.order(), definition.source().slug(),
                    definition.severity(), status, ReadinessMessageKeys.of(definition.source(), definition.id(), status),
                    Map.of(), status == ReadinessStatus.PASS ? null : "/fix/" + definition.id(), definition.needs(), false));
        }
        return new ApplicationReadiness(code, "App " + code, NOW, OverallReadinessStatus.READY, 0, 0, 0, null, steps);
    }

    /** First BLOCK check id of the application's profile that is not UAM-derived, for tests that need one that can fail. */
    static String aBlockingId(String code) {
        return ReadinessProfiles.forApplication(code).orElseThrow().stream()
                .filter(d -> d.severity() == ReadinessSeverity.BLOCK)
                .map(ReadinessStepDefinition::id)
                .filter(id -> id.startsWith("WALLET_"))
                .findFirst().orElseThrow();
    }

    /** A WARN check id of the application's profile. */
    static String aWarnId(String code) {
        return ReadinessProfiles.forApplication(code).orElseThrow().stream()
                .filter(d -> d.severity() == ReadinessSeverity.WARN)
                .map(ReadinessStepDefinition::id)
                .filter(id -> !id.endsWith("_REACHABLE"))
                .findFirst().orElseThrow();
    }

    /** Links by organization and code, as the real service keeps them. */
    static final class InMemoryLinks implements TenantApplicationServiceUseCase {
        final Map<String, TenantApplication> links = new LinkedHashMap<>();

        TenantApplication add(String tenantId, String code, TenantApplicationStatus status, List<String> owners) {
            TenantApplication link = TenantApplication.builder().id(tenantId + ":" + code).tenantId(tenantId)
                    .applicationCode(code).status(status).ownerTeamUserIds(owners).build();
            links.put(tenantId + "|" + code, link);
            return link;
        }

        @Override
        public Optional<TenantApplication> find(String tenantId, String applicationCode) {
            return Optional.ofNullable(links.get(tenantId + "|" + applicationCode));
        }

        @Override
        public List<TenantApplication> activeLinks(String applicationCode) {
            return links.values().stream().filter(l -> l.getApplicationCode().equals(applicationCode))
                    .filter(l -> l.getStatus() == TenantApplicationStatus.ACTIVE).toList();
        }

        @Override
        public TenantApplication updateOwnerTeam(String tenantId, String applicationCode, List<String> ownerTeamUserIds, String actor) {
            TenantApplication link = find(tenantId, applicationCode).orElseThrow(() -> UamException.notFound("none"));
            link.setOwnerTeamUserIds(ownerTeamUserIds);
            link.setUpdatedBy(actor);
            return link;
        }

        @Override
        public TenantApplication activateWithOverride(String tenantId, String applicationCode, GoLiveOverride override, String actor) {
            TenantApplication link = find(tenantId, applicationCode).orElseThrow(() -> UamException.notFound("none"));
            link.setStatus(TenantApplicationStatus.ACTIVE);
            link.setGoLiveOverrideBy(override.by());
            link.setGoLiveOverrideAt(override.at());
            link.setGoLiveOverrideReason(override.reason());
            link.setGoLiveOverrideSteps(override.blockingSteps());
            return link;
        }

        @Override
        public void recordAlert(String tenantId, String applicationCode, AlertKind kind, LocalDateTime at, String fingerprint) {
            find(tenantId, applicationCode).ifPresent(link -> {
                if (kind == AlertKind.BLOCKED) {
                    link.setLastBlockedAlertAt(fingerprint == null ? null : at);
                    link.setLastBlockedAlertFingerprint(fingerprint);
                } else {
                    link.setLastRefusedAlertAt(fingerprint == null ? null : at);
                    link.setLastRefusedAlertFingerprint(fingerprint);
                }
            });
        }

        @Override
        public ListOutput list(ListInput input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubscribeOutput subscribe(SubscribeInput input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UpdateOutput update(UpdateInput input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CancelOutput cancel(CancelInput input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> activeApplicationCodes(String tenantId) {
            return List.of();
        }

        @Override
        public List<String> linkedApplicationCodes(String tenantId) {
            return List.of();
        }

        @Override
        public boolean linkedElsewhere(String applicationCode, String tenantId) {
            return false;
        }

        @Override
        public void ensureSubscribed(String tenantId, String applicationCode, String actor) {
            throw new UnsupportedOperationException();
        }
    }

    /** Evaluation results by organization and code; counts calls. */
    static final class FakeReadiness implements ApplicationReadinessUseCase {
        final Map<String, ApplicationReadiness> results = new ConcurrentHashMap<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        final Set<String> failing = ConcurrentHashMap.newKeySet();

        void set(String tenantId, String code, ApplicationReadiness readiness) {
            results.put(tenantId + "|" + code, readiness);
        }

        @Override
        public ApplicationReadiness evaluateFor(String tenantId, String applicationCode) {
            calls.add(tenantId + "|" + applicationCode);
            if (failing.contains(tenantId + "|" + applicationCode)) {
                throw new IllegalStateException("evaluation broke");
            }
            ApplicationReadiness result = results.get(tenantId + "|" + applicationCode);
            return result != null ? result : GovernanceFakes.readiness(applicationCode, Map.of());
        }

        @Override
        public ApplicationReadiness readiness(String applicationCode, boolean refresh) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.heuristiq.identity.readiness.application.ReadinessSummary> summaries(boolean refresh) {
            throw new UnsupportedOperationException();
        }
    }

    /** Super admins and a directory of members by organization. */
    static final class FakeDirectory implements AlertRecipientDirectory {
        final List<AlertRecipient> superAdmins = new ArrayList<>();
        final Map<String, Map<String, AlertRecipient>> membersByTenant = new HashMap<>();

        @Override
        public List<AlertRecipient> superAdmins(int limit) {
            return superAdmins.stream().limit(limit).toList();
        }

        @Override
        public List<AlertRecipient> activeMembers(String tenantId, Collection<String> userIds) {
            Map<String, AlertRecipient> members = membersByTenant.getOrDefault(tenantId, Map.of());
            return userIds.stream().map(members::get).filter(java.util.Objects::nonNull).toList();
        }
    }

    /** Records every message; can be told to fail. */
    static final class RecordingSender implements ReadinessAlertSender {
        record Sent(String event, String userId, String tenantId, Map<String, String> variables, String entityId,
                    String triggeredBy, String alertId) {
        }

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        volatile boolean accept = true;

        @Override
        public boolean send(String event, AlertRecipient recipient, String tenantId, Map<String, String> variables, String entityId,
                            String triggeredBy, String alertId) {
            if (!accept) {
                return false;
            }
            sent.add(new Sent(event, recipient.userId(), tenantId, Map.copyOf(variables), entityId, triggeredBy, alertId));
            return true;
        }

        List<String> recipients() {
            return sent.stream().map(Sent::userId).toList();
        }
    }

    static final class RecordingAudit implements GovernanceAudit {
        record Entry(String tenantId, String userId, String entityId, boolean overridden, Map<String, Object> before,
                     Map<String, Object> after, String remarks) {
        }

        final List<Entry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void record(String tenantId, String userId, String entityId, boolean overridden, Map<String, Object> before,
                           Map<String, Object> after, String remarks) {
            entries.add(new Entry(tenantId, userId, entityId, overridden, before, after, remarks));
        }
    }

    static final class FakeCaller implements GovernanceCaller {
        boolean superAdmin;
        String userId = "user-1";

        @Override
        public boolean isSuperAdmin() {
            return superAdmin;
        }

        @Override
        public String userId() {
            return userId;
        }
    }

    static final class FakeCandidates implements OwnerCandidateDirectory {
        final Map<String, List<OwnerCandidate>> byTenant = new HashMap<>();

        @Override
        public List<OwnerCandidate> departmentHeads(String tenantId) {
            return byTenant.getOrDefault(tenantId, List.of());
        }
    }

    /** A lease that can be held by someone else; counts acquire and release. */
    static final class FakeLease implements SchedulerLease {
        volatile boolean available = true;
        int acquired;
        int released;
        String lastName;

        @Override
        public boolean tryAcquire(String name, Duration ttl) {
            lastName = name;
            if (available) {
                acquired++;
            }
            return available;
        }

        @Override
        public void release(String name) {
            released++;
        }
    }

    static AlertRecipient person(String id, String email, String language) {
        return new AlertRecipient(id, "Name " + id, email, language);
    }
}
