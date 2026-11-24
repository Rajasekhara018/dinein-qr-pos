package com.heuristiq.uam.readiness.application;

import static com.heuristiq.uam.readiness.application.GovernanceFakes.WALLET_APP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.application.SessionOrganizationResolver;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeCandidates;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeCaller;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeDirectory;
import com.heuristiq.uam.readiness.application.GovernanceFakes.FakeReadiness;
import com.heuristiq.uam.readiness.application.GovernanceFakes.InMemoryLinks;
import com.heuristiq.uam.readiness.application.GovernanceFakes.RecordingAudit;
import com.heuristiq.uam.readiness.application.GovernanceFakes.RecordingSender;
import com.heuristiq.uam.readiness.application.port.OwnerCandidate;
import com.heuristiq.uam.readiness.domain.GovernanceConstants;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;

/** Override (SUPER_ADMIN, reason, audited, recorded), owner team (department heads of the session organization), state. */
class ReadinessGovernanceServiceTest {

    private String sessionOrg = "org-a";
    private final InMemoryLinks links = new InMemoryLinks();
    private final FakeReadiness readiness = new FakeReadiness();
    private final FakeCandidates candidates = new FakeCandidates();
    private final FakeDirectory directory = new FakeDirectory();
    private final RecordingSender sender = new RecordingSender();
    private final RecordingAudit audit = new RecordingAudit();
    private final FakeCaller caller = new FakeCaller();
    private ReadinessGovernanceService service;

    @BeforeEach
    void setUp() {
        SessionOrganizationResolver session = mock(SessionOrganizationResolver.class);
        when(session.sessionOrganizationId()).thenAnswer(invocation -> sessionOrg);
        ReadinessAlertService alerts = new ReadinessAlertService(links, directory, sender, GovernanceFakes.properties(),
                GovernanceFakes.clock());
        service = new ReadinessGovernanceService(session, links, readiness, candidates, alerts, audit, caller,
                GovernanceFakes.properties(), GovernanceFakes.clock());
        links.add("org-a", WALLET_APP, TenantApplicationStatus.SUSPENDED, List.of("head-1"));
        links.add("org-b", WALLET_APP, TenantApplicationStatus.SUSPENDED, List.of());
        directory.superAdmins.add(GovernanceFakes.person("sa-1", "sa@example.org", "en"));
        directory.membersByTenant.put("org-a", Map.of("head-1", GovernanceFakes.person("head-1", "head@example.org", "ta")));
        candidates.byTenant.put("org-a", List.of(new OwnerCandidate("head-1", "Head One", "FIN", "Finance"),
                new OwnerCandidate("head-2", "Head Two", "OPS", "Operations")));
        candidates.byTenant.put("org-b", List.of(new OwnerCandidate("head-9", "Other", "X", "Other dept")));
    }

    private String failBlockingStep() {
        String blocking = GovernanceFakes.aBlockingId(WALLET_APP);
        readiness.set("org-a", WALLET_APP, GovernanceFakes.readiness(WALLET_APP, Map.of(blocking, ReadinessStatus.FAIL)));
        return blocking;
    }

    @Test
    void organizationAdminCannotOverride() {
        failBlockingStep();
        caller.superAdmin = false;

        assertThatThrownBy(() -> service.override(WALLET_APP, "Customer signed off the risk"))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo(GovernanceConstants.OVERRIDE_FORBIDDEN_MESSAGE);
                });
        assertThat(links.find("org-a", WALLET_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
        assertThat(audit.entries).isEmpty();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void reasonMustBeWithinTheConfiguredLengths() {
        failBlockingStep();
        caller.superAdmin = true;

        assertThatThrownBy(() -> service.override(WALLET_APP, "too short"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.override(WALLET_APP, null)).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.override(WALLET_APP, "   " + "x".repeat(501) + "   ")).isInstanceOf(UamException.class);
        assertThat(links.find("org-a", WALLET_APP).orElseThrow().getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
    }

    @Test
    void superAdminOverrideActivatesRecordsAuditsAndAlerts() {
        String blocking = failBlockingStep();
        caller.superAdmin = true;
        caller.userId = "sa-1";

        TenantApplication saved = service.override("  " + WALLET_APP.toLowerCase() + " ", "  Go-live approved by the board  ");

        assertThat(saved.getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
        assertThat(saved.getGoLiveOverrideBy()).isEqualTo("sa-1");
        assertThat(saved.getGoLiveOverrideReason()).isEqualTo("Go-live approved by the board");
        assertThat(saved.getGoLiveOverrideSteps()).contains(blocking);
        assertThat(saved.getGoLiveOverrideAt()).isNotNull();

        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.overridden()).isTrue();
            assertThat(entry.tenantId()).isEqualTo("org-a");
            assertThat(entry.userId()).isEqualTo("sa-1");
            assertThat(entry.entityId()).isEqualTo("org-a:" + WALLET_APP);
            assertThat(entry.before()).containsEntry("status", "SUSPENDED");
            assertThat(entry.after()).containsEntry("status", "ACTIVE").containsEntry("reason", "Go-live approved by the board");
            assertThat((String) entry.after().get("blockingSteps")).contains(blocking);
        });
        assertThat(sender.recipients()).containsExactlyInAnyOrder("sa-1", "head-1");
        assertThat(sender.sent).allSatisfy(s -> {
            assertThat(s.event()).isEqualTo(GovernanceConstants.EVENT_GO_LIVE_OVERRIDDEN);
            assertThat(s.variables()).containsEntry(GovernanceConstants.VAR_REASON, "Go-live approved by the board");
        });
    }

    @Test
    void overrideWithNothingBlockingIsRefused() {
        caller.superAdmin = true;

        assertThatThrownBy(() -> service.override(WALLET_APP, "Nothing is blocking but try anyway"))
                .isInstanceOfSatisfying(UamException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(GovernanceConstants.NOTHING_TO_OVERRIDE_MESSAGE));
    }

    @Test
    void overrideNeedsALinkOfTheSessionOrganization() {
        failBlockingStep();
        caller.superAdmin = true;
        sessionOrg = "org-without-link";

        assertThatThrownBy(() -> service.override(WALLET_APP, "A perfectly good reason"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.override("NOT_AN_APP", "A perfectly good reason"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        links.find("org-a", WALLET_APP).orElseThrow().setStatus(TenantApplicationStatus.CANCELLED);
        sessionOrg = "org-a";
        assertThatThrownBy(() -> service.override(WALLET_APP, "A perfectly good reason"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void ownerTeamAcceptsOnlyDepartmentHeadsOfTheSessionOrganization() {
        TenantApplication saved = service.updateOwnerTeam(WALLET_APP, List.of(" head-2 ", "head-1", "head-1", ""));

        assertThat(saved.getOwnerTeamUserIds()).containsExactly("head-2", "head-1");
        // head-9 heads a department of org-b only.
        assertThatThrownBy(() -> service.updateOwnerTeam(WALLET_APP, List.of("head-9")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getMessage()).startsWith(GovernanceConstants.OWNER_NOT_ELIGIBLE_MESSAGE).contains("head-9");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThat(links.find("org-b", WALLET_APP).orElseThrow().getOwnerTeamUserIds()).isEmpty();
    }

    @Test
    void ownerTeamCanBeClearedAndIsCapped() {
        assertThat(service.updateOwnerTeam(WALLET_APP, null).getOwnerTeamUserIds()).isEmpty();
        assertThat(service.updateOwnerTeam(WALLET_APP, List.of()).getOwnerTeamUserIds()).isEmpty();
        assertThatThrownBy(() -> service.updateOwnerTeam(WALLET_APP, List.of("a", "b", "c", "d")))
                .isInstanceOfSatisfying(UamException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(String.format(GovernanceConstants.OWNER_TEAM_TOO_LARGE_MESSAGE, 3)));
    }

    @Test
    void ownerTeamNeedsALink() {
        sessionOrg = "org-without-link";
        assertThatThrownBy(() -> service.updateOwnerTeam(WALLET_APP, List.of()))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void candidatesAreTheSessionOrganizationsDepartmentHeads() {
        assertThat(service.ownerCandidates()).extracting(OwnerCandidate::userId).containsExactly("head-1", "head-2");
        sessionOrg = "org-b";
        assertThat(service.ownerCandidates()).extracting(OwnerCandidate::userId).containsExactly("head-9");
        sessionOrg = "org-unknown";
        assertThat(service.ownerCandidates()).isEmpty();
    }

    @Test
    void stateShowsOwnersOverrideAndAlertTimes() {
        ReadinessGovernanceService.State initial = service.state(WALLET_APP);
        assertThat(initial.ownerTeamUserIds()).containsExactly("head-1");
        assertThat(initial.override()).isNull();
        assertThat(initial.guardEnabled()).isTrue();
        assertThat(initial.alerts().lastBlockedAt()).isNull();

        failBlockingStep();
        caller.superAdmin = true;
        service.override(WALLET_APP, "Approved by the steering group");
        ReadinessGovernanceService.State after = service.state(WALLET_APP);
        assertThat(after.override().by()).isEqualTo("user-1");
        assertThat(after.override().reason()).isEqualTo("Approved by the steering group");
        assertThat(after.override().blockingSteps()).isNotEmpty();

        // org-b's state is its own.
        sessionOrg = "org-b";
        assertThat(service.state(WALLET_APP).override()).isNull();
    }
}
