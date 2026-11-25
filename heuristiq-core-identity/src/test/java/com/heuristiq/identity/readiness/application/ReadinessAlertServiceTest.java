package com.heuristiq.identity.readiness.application;

import static com.heuristiq.identity.readiness.application.GovernanceFakes.WALLET_APP;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.readiness.application.GovernanceFakes.FakeDirectory;
import com.heuristiq.identity.readiness.application.GovernanceFakes.InMemoryLinks;
import com.heuristiq.identity.readiness.application.GovernanceFakes.RecordingSender;
import com.heuristiq.identity.readiness.domain.GoLiveBlocker;
import com.heuristiq.identity.readiness.domain.GovernanceConstants;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;

/** Recipients (super admins plus this organization's owner team), content and the 24 hour throttle. */
class ReadinessAlertServiceTest {

    private final InMemoryLinks links = new InMemoryLinks();
    private final FakeDirectory directory = new FakeDirectory();
    private final RecordingSender sender = new RecordingSender();
    private Instant now = GovernanceFakes.NOW;
    private ReadinessAlertService alerts;

    private static GoLiveBlocker blocker(String id) {
        return new GoLiveBlocker(id, ReadinessStatus.FAIL, "readiness.x." + id, Map.of(), null);
    }

    @BeforeEach
    void setUp() {
        rebuild(GovernanceFakes.properties());
        directory.superAdmins.add(GovernanceFakes.person("sa-1", "sa1@example.org", "en"));
        directory.superAdmins.add(GovernanceFakes.person("sa-2", "sa2@example.org", "ar"));
        directory.superAdmins.add(GovernanceFakes.person("sa-noemail", " ", "en"));
        directory.membersByTenant.put("org-a", Map.of("head-a", GovernanceFakes.person("head-a", "a@example.org", "hi"),
                "sa-1", GovernanceFakes.person("sa-1", "sa1@example.org", "en")));
        directory.membersByTenant.put("org-b", Map.of("head-b", GovernanceFakes.person("head-b", "b@example.org", "te")));
        links.add("org-a", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of("head-a", "sa-1", "former-member"));
        links.add("org-b", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of("head-b"));
    }

    private void rebuild(com.heuristiq.identity.readiness.config.GovernanceProperties properties) {
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        alerts = new ReadinessAlertService(links, directory, sender, properties, clock);
    }

    @Test
    void blockedAlertGoesToSuperAdminsWithAnEmailAndTheOwnerTeamOfThatOrganizationOnly() {
        boolean sent = alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("WALLET_APP_REGISTERED")));

        assertThat(sent).isTrue();
        // sa-1 is both a super admin and in the owner team: one message. former-member is not an active member: none.
        assertThat(sender.recipients()).containsExactlyInAnyOrder("sa-1", "sa-2", "head-a");
        assertThat(sender.sent).noneMatch(s -> s.userId().equals("head-b"));
        assertThat(sender.sent).allSatisfy(s -> {
            assertThat(s.event()).isEqualTo(GovernanceConstants.EVENT_READINESS_BLOCKED);
            assertThat(s.tenantId()).isEqualTo("org-a");
            assertThat(s.entityId()).isEqualTo("org-a:" + WALLET_APP);
            assertThat(s.triggeredBy()).isNull();
            assertThat(s.variables()).containsEntry(GovernanceConstants.VAR_APPLICATION_NAME, "Wallet")
                    .containsEntry(GovernanceConstants.VAR_BLOCKING_STEPS, "WALLET_APP_REGISTERED")
                    .containsKey(GovernanceConstants.VAR_RECIPIENT_NAME);
        });
        assertThat(sender.sent.stream().map(RecordingSender.Sent::alertId).distinct()).hasSize(1);
    }

    @Test
    void anEmptyOwnerTeamStillAlertsTheSuperAdminsAndNeverFails() {
        links.add("org-c", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of());

        boolean sent = alerts.blocked("org-c", WALLET_APP, "Wallet", List.of(blocker("WALLET_APP_REGISTERED")));

        assertThat(sent).isTrue();
        assertThat(sender.recipients()).containsExactlyInAnyOrder("sa-1", "sa-2");
        assertThat(alerts.refused("org-c", WALLET_APP, "Wallet", List.of(blocker("WALLET_APP_ENABLED")), "u-9")).isTrue();
    }

    @Test
    void aMissingLinkOrAMissingOwnerTeamAlsoLeavesTheSuperAdminsAsRecipients() {
        assertThat(alerts.recipients("org-x", WALLET_APP)).extracting(r -> r.userId()).containsExactlyInAnyOrder("sa-1", "sa-2");
    }

    @Test
    void sameBlockingSetIsNotAlertedAgainWithinTheInterval() {
        List<GoLiveBlocker> blockers = List.of(blocker("A_STEP"), blocker("B_STEP"));
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", blockers)).isTrue();
        int first = sender.sent.size();

        now = now.plus(Duration.ofHours(23));
        // Same set, any order: throttled.
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("B_STEP"), blocker("A_STEP")))).isFalse();
        assertThat(sender.sent).hasSize(first);

        now = now.plus(Duration.ofHours(2));
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", blockers)).isTrue();
        assertThat(sender.sent).hasSize(first * 2);
    }

    @Test
    void aDifferentBlockingSetAlertsAtOnce() {
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isTrue();
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP"), blocker("B_STEP")))).isTrue();
    }

    @Test
    void throttleIsPerOrganization() {
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isTrue();
        assertThat(alerts.blocked("org-b", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isTrue();
        assertThat(sender.sent).anyMatch(s -> s.tenantId().equals("org-b") && s.userId().equals("head-b"));
        assertThat(sender.sent.stream().filter(s -> s.tenantId().equals("org-b")).map(RecordingSender.Sent::userId))
                .doesNotContain("head-a");
    }

    @Test
    void throttleStateIsOnlySavedWhenNmsAcceptedAMessage() {
        sender.accept = false;
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isFalse();
        assertThat(links.find("org-a", WALLET_APP).orElseThrow().getLastBlockedAlertFingerprint()).isNull();

        sender.accept = true;
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isTrue();
        TenantApplication link = links.find("org-a", WALLET_APP).orElseThrow();
        assertThat(link.getLastBlockedAlertFingerprint()).isNotBlank();
        assertThat(link.getLastBlockedAlertAt()).isNotNull();
    }

    @Test
    void clearedForgetsTheStateSoTheNextFailureAlertsAtOnce() {
        List<GoLiveBlocker> blockers = List.of(blocker("A_STEP"));
        alerts.blocked("org-a", WALLET_APP, "Wallet", blockers);
        TenantApplication link = links.find("org-a", WALLET_APP).orElseThrow();

        alerts.cleared("org-a", WALLET_APP, link);

        assertThat(link.getLastBlockedAlertFingerprint()).isNull();
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", blockers)).isTrue();
    }

    @Test
    void clearedDoesNothingWhenNothingWasAlerted() {
        TenantApplication link = links.find("org-b", WALLET_APP).orElseThrow();
        alerts.cleared("org-b", WALLET_APP, link);
        alerts.cleared("org-b", WALLET_APP, null);
        assertThat(link.getLastBlockedAlertFingerprint()).isNull();
    }

    @Test
    void refusedAndBlockedAreThrottledSeparately() {
        List<GoLiveBlocker> blockers = List.of(blocker("A_STEP"));
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", blockers)).isTrue();
        assertThat(alerts.refused("org-a", WALLET_APP, "Wallet", blockers, "u-9")).isTrue();
        assertThat(alerts.refused("org-a", WALLET_APP, "Wallet", blockers, "u-9")).isFalse();
        assertThat(sender.sent).anyMatch(s -> s.event().equals(GovernanceConstants.EVENT_GO_LIVE_REFUSED)
                && s.triggeredBy().equals("u-9"));
    }

    @Test
    void overrideAlertIsNeverThrottled() {
        List<GoLiveBlocker> blockers = List.of(blocker("A_STEP"));
        assertThat(alerts.overridden("org-a", WALLET_APP, "Wallet", blockers, "sa-1", "Because")).isTrue();
        assertThat(alerts.overridden("org-a", WALLET_APP, "Wallet", blockers, "sa-1", "Because")).isTrue();
        assertThat(sender.sent).anyMatch(s -> s.variables().get(GovernanceConstants.VAR_OVERRIDDEN_BY).equals("sa-1")
                && s.variables().get(GovernanceConstants.VAR_REASON).equals("Because"));
    }

    @Test
    void disabledAlertsSendNothing() {
        rebuild(GovernanceFakes.properties(true, false));
        List<GoLiveBlocker> blockers = List.of(blocker("A_STEP"));

        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", blockers)).isFalse();
        assertThat(alerts.refused("org-a", WALLET_APP, "Wallet", blockers, "u")).isFalse();
        assertThat(alerts.overridden("org-a", WALLET_APP, "Wallet", blockers, "u", "r")).isFalse();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void noBlockersMeansNoAlert() {
        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of())).isFalse();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void noRecipientsMeansNothingSentAndNoStateSaved() {
        directory.superAdmins.clear();
        links.add("org-c", WALLET_APP, TenantApplicationStatus.ACTIVE, List.of());

        assertThat(alerts.blocked("org-c", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isFalse();
        assertThat(links.find("org-c", WALLET_APP).orElseThrow().getLastBlockedAlertFingerprint()).isNull();
    }

    @Test
    void anUnexpectedFailureWhileSendingIsSwallowed() {
        directory.superAdmins.clear();
        directory.superAdmins.add(null);

        assertThat(alerts.blocked("org-a", WALLET_APP, "Wallet", List.of(blocker("A_STEP")))).isFalse();
    }

    @Test
    void recipientCapIsRespected() {
        for (int i = 0; i < 80; i++) {
            directory.superAdmins.add(GovernanceFakes.person("many-" + i, "m" + i + "@example.org", "en"));
        }
        assertThat(alerts.recipients("org-a", WALLET_APP)).hasSize(50);
    }
}
