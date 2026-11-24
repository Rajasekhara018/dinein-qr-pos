package com.heuristiq.uam.readiness.application;

import static com.heuristiq.uam.readiness.application.GovernanceFakes.WALLET_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeCaller;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeDirectory;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeReadiness;
import com.heuristiq.uam.readiness.application.GovernanceFakes.InMemoryLinks;
import com.heuristiq.uam.readiness.application.GovernanceFakes.RecordingAudit;
import com.heuristiq.uam.readiness.application.GovernanceFakes.RecordingSender;
import com.heuristiq.uam.readiness.config.GovernanceProperties;
import com.heuristiq.uam.readiness.domain.GoLiveRefusedException;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;
import com.heuristiq.uam.readiness.domain.ReadinessCheckIds;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;

/** The go-live guard: blocks on failing or unknown BLOCK steps, lets WARN-only through, can be switched off. */
class GoLiveGuardServiceTest {

    private static final String ORG = "org-a";

    private final FakeReadiness readiness = new FakeReadiness();
    private final InMemoryLinks links = new InMemoryLinks();
    private final FakeDirectory directory = new FakeDirectory();
    private final RecordingSender sender = new RecordingSender();
    private final RecordingAudit audit = new RecordingAudit();
    private final FakeCaller caller = new FakeCaller();

    @BeforeEach
    void setUp() {
        links.add(ORG, WALLET_APP, TenantApplicationStatus.SUSPENDED, java.util.List.of("head-1"));
        directory.superAdmins.add(GovernanceFakes.person("sa-1", "sa@example.org", "en"));
        directory.membersByTenant.put(ORG, Map.of("head-1", GovernanceFakes.person("head-1", "head@example.org", "hi")));
    }

    private GoLiveGuardService guard(GovernanceProperties properties) {
        ReadinessAlertService alerts = new ReadinessAlertService(links, directory, sender, properties, GovernanceFakes.clock());
        return new GoLiveGuardService(readiness, alerts, audit, caller, properties);
    }

    @Test
    void failingBlockStepRefusesWithTypedConflictListingTheSteps() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        assertThatThrownBy(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP))
                .isInstanceOfSatisfying(GoLiveRefusedException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getApplicationCode()).isEqualTo(WALLET_APP);
                    assertThat(e.getBlockers()).extracting(b -> b.stepId()).contains(blocking);
                    assertThat(e.getBlockers()).allSatisfy(b -> assertThat(b.messageKey()).startsWith("readiness."));
                    assertThat(e.getMessage()).isEqualTo(GovernanceConstants.GO_LIVE_REFUSED_MESSAGE);
                });
    }

    @Test
    void theGuardBuiltFromTheDefaultPropertiesRefusesAFailingRequiredStep() {
        com.heuristiq.uam.readiness.config.GovernanceProperties defaults = new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("railcab.uam.readiness.governance", com.heuristiq.uam.readiness.config.GovernanceProperties.class);
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        assertThatThrownBy(() -> guard(defaults).requireReady(ORG, WALLET_APP)).isInstanceOf(GoLiveRefusedException.class);
    }

    @Test
    void unknownBlockStepAlsoRefuses() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.UNKNOWN)));

        assertThatThrownBy(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP))
                .isInstanceOfSatisfying(GoLiveRefusedException.class,
                        e -> assertThat(e.getBlockers()).anySatisfy(b -> {
                            assertThat(b.stepId()).isEqualTo(blocking);
                            assertThat(b.status()).isEqualTo(ReadinessStatus.UNKNOWN);
                        }));
    }

    @Test
    void failingWarnStepsNeverBlock() {
        String warn = GovernanceFakes.aWarnId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP,
                Map.of(warn, ReadinessStatus.FAIL)));

        assertThatCode(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP)).doesNotThrowAnyException();
        assertThat(sender.sent).isEmpty();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void theLinkNotBeingActiveYetDoesNotBlockItsOwnActivation() {
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP,
                Map.of(ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION, ReadinessStatus.FAIL)));

        assertThatCode(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP)).doesNotThrowAnyException();
    }

    @Test
    void switchedOffGuardNeverEvaluates() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        assertThatCode(() -> guard(GovernanceFakes.properties(false, true)).requireReady(ORG, WALLET_APP)).doesNotThrowAnyException();
        assertThat(readiness.calls).isEmpty();
    }

    @Test
    void applicationWithoutProfileIsNotGuarded() {
        assertThatCode(() -> guard(GovernanceFakes.properties()).requireReady(ORG, "SOME_SUPPORT_APP")).doesNotThrowAnyException();
        assertThat(readiness.calls).isEmpty();
    }

    @Test
    void refusalIsAuditedAndAlertedToSuperAdminsAndTheOwnerTeam() {
        caller.userId = "admin-9";
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        assertThatThrownBy(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP))
                .isInstanceOf(GoLiveRefusedException.class);

        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.tenantId()).isEqualTo(ORG);
            assertThat(entry.userId()).isEqualTo("admin-9");
            assertThat(entry.overridden()).isFalse();
            assertThat(entry.after()).containsEntry("applicationCode", WALLET_APP);
            assertThat((String) entry.after().get("blockingSteps")).contains(blocking);
        });
        assertThat(sender.recipients()).containsExactlyInAnyOrder("sa-1", "head-1");
        assertThat(sender.sent).allSatisfy(s -> {
            assertThat(s.event()).isEqualTo(GovernanceConstants.EVENT_GO_LIVE_REFUSED);
            assertThat(s.entityId()).isEqualTo(ORG + ":" + WALLET_APP);
            assertThat(s.triggeredBy()).isEqualTo("admin-9");
            assertThat(s.variables()).containsEntry(GovernanceConstants.VAR_ATTEMPTED_BY, "admin-9");
        });
    }

    @Test
    void anAlertFailureNeverHidesTheRefusal() {
        sender.accept = false;
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        assertThatThrownBy(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP))
                .isInstanceOf(GoLiveRefusedException.class);
        // Nothing was accepted, so the throttle state is not saved and a later attempt alerts again.
        assertThat(links.find(ORG, WALLET_APP).orElseThrow().getLastRefusedAlertFingerprint()).isNull();
    }

    @Test
    void repeatedRefusalsOfTheSameProblemAreThrottled() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set(ORG, WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));
        GoLiveGuardService guard = guard(GovernanceFakes.properties());

        assertThatThrownBy(() -> guard.requireReady(ORG, WALLET_APP)).isInstanceOf(GoLiveRefusedException.class);
        int afterFirst = sender.sent.size();
        assertThatThrownBy(() -> guard.requireReady(ORG, WALLET_APP)).isInstanceOf(GoLiveRefusedException.class);

        assertThat(afterFirst).isEqualTo(2);
        assertThat(sender.sent).hasSize(afterFirst);
        // Every refusal is still audited.
        assertThat(audit.entries).hasSize(2);
    }

    @Test
    void anotherOrganizationsApplicationIsEvaluatedForItself() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set("org-b", WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));

        // org-a has no failing step, org-b does: the guard follows the named organization.
        assertThatCode(() -> guard(GovernanceFakes.properties()).requireReady(ORG, WALLET_APP)).doesNotThrowAnyException();
        links.add("org-b", WALLET_APP, TenantApplicationStatus.SUSPENDED, java.util.List.of());
        assertThatThrownBy(() -> guard(GovernanceFakes.properties()).requireReady("org-b", WALLET_APP))
                .isInstanceOf(GoLiveRefusedException.class);
        assertThat(readiness.calls).containsExactly(ORG + "|" + WALLET_APP, "org-b|" + WALLET_APP);
    }
}
