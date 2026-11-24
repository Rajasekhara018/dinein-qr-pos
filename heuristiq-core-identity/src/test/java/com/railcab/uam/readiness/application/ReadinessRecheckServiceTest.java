package com.heuristiq.uam.readiness.application;

import static com.heuristiq.uam.readiness.application.GovernanceFakes.WALLET_APP;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeDirectory;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeLease;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeReadiness;
import com.heuristiq.uam.readiness.application.GovernanceFakes.InMemoryLinks;
import com.heuristiq.uam.readiness.application.GovernanceFakes.RecordingSender;
import com.heuristiq.uam.readiness.config.GovernanceProperties;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;
import com.heuristiq.uam.security.TenantContext;

/** The scheduled re-check: lease, idempotency, bounds, tenant isolation and failure containment. */
class ReadinessRecheckServiceTest {

    private final InMemoryLinks links = new InMemoryLinks();
    private final FakeReadiness readiness = new FakeReadiness();
    private final FakeDirectory directory = new FakeDirectory();
    private final RecordingSender sender = new RecordingSender();
    private final FakeLease lease = new FakeLease();
    private final Set<String> inactiveOrganizations = ConcurrentHashMap.newKeySet();
    private GovernanceProperties properties = GovernanceFakes.properties();
    private ReadinessRecheckService recheck;

    @BeforeEach
    void setUp() {
        directory.superAdmins.add(GovernanceFakes.person("sa-1", "sa@example.org", "en"));
        directory.membersByTenant.put("org-a", Map.of("head-a", GovernanceFakes.person("head-a", "a@example.org", "hi")));
        directory.membersByTenant.put("org-b", Map.of("head-b", GovernanceFakes.person("head-b", "b@example.org", "te")));
        links.add("org-a", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of("head-a"));
        links.add("org-b", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of("head-b"));
        build();
    }

    private void build() {
        ReadinessAlertService alerts = new ReadinessAlertService(links, directory, sender, properties, GovernanceFakes.clock());
        Predicate<String> active = tenant -> !inactiveOrganizations.contains(tenant);
        recheck = new ReadinessRecheckService(links, readiness, alerts, lease, active, properties);
    }

    private void failOrgA() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set("org-a", WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));
    }

    @Test
    void anotherInstanceHoldingTheLeaseMeansNothingRuns() {
        failOrgA();
        lease.available = false;

        ReadinessRecheckService.RunResult result = recheck.runOnce();

        assertThat(result.ran()).isFalse();
        assertThat(readiness.calls).isEmpty();
        assertThat(sender.sent).isEmpty();
        assertThat(lease.released).isZero();
        assertThat(lease.lastName).isEqualTo(ReadinessRecheckService.LEASE_NAME);
    }

    @Test
    void blockingOfOneOrganizationNeverAlertsAnother() {
        failOrgA();

        ReadinessRecheckService.RunResult result = recheck.runOnce();

        assertThat(result).isEqualTo(new ReadinessRecheckService.RunResult(true, 2, 1, 0));
        assertThat(sender.recipients()).containsExactlyInAnyOrder("sa-1", "head-a");
        assertThat(sender.sent).allSatisfy(s -> {
            assertThat(s.tenantId()).isEqualTo("org-a");
            assertThat(s.event()).isEqualTo(GovernanceConstants.EVENT_READINESS_BLOCKED);
        });
        assertThat(readiness.calls).containsExactlyInAnyOrder("org-a|" + WALLET_APP, "org-b|" + WALLET_APP);
        assertThat(lease.released).isEqualTo(1);
    }

    @Test
    void secondRunIsIdempotentWithinTheInterval() {
        failOrgA();
        recheck.runOnce();
        int sentAfterFirst = sender.sent.size();

        ReadinessRecheckService.RunResult second = recheck.runOnce();

        assertThat(second.alerted()).isZero();
        assertThat(sender.sent).hasSize(sentAfterFirst);
    }

    @Test
    void afterTheProblemIsFixedAndReturnsItAlertsAgain() {
        failOrgA();
        recheck.runOnce();
        readiness.set("org-a", WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of()));
        recheck.runOnce();
        assertThat(links.find("org-a", WALLET_APP).orElseThrow().getLastBlockedAlertFingerprint()).isNull();

        int before = sender.sent.size();
        failOrgA();
        assertThat(recheck.runOnce().alerted()).isEqualTo(1);
        assertThat(sender.sent.size()).isGreaterThan(before);
    }

    @Test
    void onlyActiveLinksOfInServiceOrganizationsAreChecked() {
        links.add("org-c", WALLET_APP, TenantApplicationStatus.SUSPENDED, List.of());
        links.add("org-d", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of());
        inactiveOrganizations.add("org-d");

        recheck.runOnce();

        assertThat(readiness.calls).containsExactlyInAnyOrder("org-a|" + WALLET_APP, "org-b|" + WALLET_APP);
    }

    @Test
    void aRunIsBoundedByTheMaximumNumberOfLinks() {
        properties = new GovernanceProperties(true, 10, 500, 3, Duration.ofHours(24), true, 50, true, Duration.ofHours(6),
                Duration.ofMinutes(5), 1, 1, Duration.ofMinutes(30));
        build();

        ReadinessRecheckService.RunResult result = recheck.runOnce();

        assertThat(result.checked()).isEqualTo(1);
        // Deterministic order: org-a first.
        assertThat(readiness.calls).containsExactly("org-a|" + WALLET_APP);
    }

    @Test
    void oneFailingCheckDoesNotStopTheOthers() {
        readiness.failing.add("org-a|" + WALLET_APP);
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set("org-b", WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.UNKNOWN)));

        ReadinessRecheckService.RunResult result = recheck.runOnce();

        assertThat(result).isEqualTo(new ReadinessRecheckService.RunResult(true, 1, 1, 1));
        assertThat(sender.sent).allSatisfy(s -> assertThat(s.tenantId()).isEqualTo("org-b"));
    }

    @Test
    void noActiveLinksStillReleasesTheLease() {
        InMemoryLinks empty = new InMemoryLinks();
        ReadinessAlertService alerts = new ReadinessAlertService(empty, directory, sender, properties, GovernanceFakes.clock());
        ReadinessRecheckService none = new ReadinessRecheckService(empty, readiness, alerts, lease, t -> true, properties);

        assertThat(none.runOnce()).isEqualTo(new ReadinessRecheckService.RunResult(true, 0, 0, 0));
        assertThat(lease.released).isEqualTo(1);
    }

    @Test
    void tenantContextIsNotLeftBehindOnTheWorkerThreadsOrTheCaller() {
        failOrgA();
        recheck.runOnce();
        assertThat(TenantContext.get()).isNull();
    }
}
