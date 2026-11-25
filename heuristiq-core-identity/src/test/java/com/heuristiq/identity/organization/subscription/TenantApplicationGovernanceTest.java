package com.heuristiq.identity.organization.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase.AlertKind;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase.GoLiveOverride;
import com.heuristiq.identity.organization.subscription.repository.TenantApplicationRepository;
import com.heuristiq.identity.readiness.domain.GoLiveRefusedException;

/** Switching an application on goes through the go-live guard; owner team, override and alert state live on the link. */
class TenantApplicationGovernanceTest {

    private static final String TENANT = "org-1";
    private static final String CODE = "CENTRALISED_WALLET_APP";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
    private final List<TenantApplication> rows = new ArrayList<>();
    private GoLiveGuard guard;
    private TenantApplicationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantRepository tenants = mock(TenantRepository.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        when(tenants.findById(anyString())).thenAnswer(inv -> Optional.of(Tenant.builder().id(inv.getArgument(0))
                .status(TenantStatus.ACTIVE).build()));
        when(applications.findByCodeIgnoreCase(CODE)).thenReturn(Optional.of(Application.builder().code(CODE).name("Wallet").active(true).build()));
        service = new TenantApplicationService(tenants, applications, new InMemory(), clock);
        guard = mock(GoLiveGuard.class);
        ObjectProvider<GoLiveGuard> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        service.setGoLiveGuard(provider);
    }

    private TenantApplication link(String tenant, TenantApplicationStatus status) {
        TenantApplication link = TenantApplication.builder().id(UUID.randomUUID().toString()).tenantId(tenant)
                .applicationCode(CODE).status(status).build();
        rows.add(link);
        return link;
    }

    private TenantApplicationServiceUseCase.UpdateOutput update(TenantApplicationStatus status, String note) {
        return service.update(new TenantApplicationServiceUseCase.UpdateInput(TENANT, CODE,
                new UpdateTenantApplicationRequest(status, null, null, note), "admin"));
    }

    @Test
    void switchingOnAsuspendedLinkRunsTheGuardFirst() {
        link(TENANT, TenantApplicationStatus.SUSPENDED);

        update(TenantApplicationStatus.ACTIVE, null);

        verify(guard).requireReady(TENANT, CODE);
        assertThat(rows.get(0).getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
    }

    @Test
    void aRefusedGuardLeavesTheLinkOffAndPassesTheTypedConflictOn() {
        link(TENANT, TenantApplicationStatus.SUSPENDED);
        doThrow(new GoLiveRefusedException(CODE, List.of())).when(guard).requireReady(TENANT, CODE);

        assertThatThrownBy(() -> update(TenantApplicationStatus.ACTIVE, null)).isInstanceOf(GoLiveRefusedException.class);

        assertThat(rows.get(0).getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
    }

    @Test
    void otherChangesAndSwitchingOffAreNotGuarded() {
        link(TENANT, TenantApplicationStatus.ACTIVE);

        update(null, "just a note");
        update(TenantApplicationStatus.ACTIVE, null);
        update(TenantApplicationStatus.SUSPENDED, null);

        verify(guard, never()).requireReady(anyString(), anyString());
    }

    @Test
    void turningAWithdrawnSubscriptionBackOnIsGuardedButANewOneIsNot() {
        service.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(TENANT,
                new SubscribeTenantApplicationRequest(CODE, null, null, null), "admin"));
        verify(guard, never()).requireReady(anyString(), anyString());

        service.cancel(new TenantApplicationServiceUseCase.CancelInput(TENANT, CODE, "admin"));
        service.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(TENANT,
                new SubscribeTenantApplicationRequest(CODE, null, null, null), "admin"));

        verify(guard).requireReady(TENANT, CODE);
    }

    @Test
    void recreatingARemovedApplicationIsSetupAndIsNotGuarded() {
        service.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(TENANT,
                new SubscribeTenantApplicationRequest(CODE, null, null, null), "admin"));
        service.cancel(new TenantApplicationServiceUseCase.CancelInput(TENANT, CODE, "admin"));
        doThrow(new GoLiveRefusedException(CODE, List.of())).when(guard).requireReady(TENANT, CODE);

        service.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(TENANT,
                new SubscribeTenantApplicationRequest(CODE, null, null, null), "admin", true));

        verify(guard, never()).requireReady(anyString(), anyString());
        assertThat(rows.get(0).getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
    }

    @Test
    void withoutAGuardBeanNothingIsChecked() {
        TenantApplicationService unguarded = new TenantApplicationService(mock(TenantRepository.class), mock(ApplicationRepository.class),
                new InMemory(), clock);
        assertThat(unguarded).isNotNull();
        link(TENANT, TenantApplicationStatus.SUSPENDED);
        service.setGoLiveGuard(null);

        update(TenantApplicationStatus.ACTIVE, null);

        verify(guard, never()).requireReady(anyString(), anyString());
    }

    @Test
    void overrideActivatesWithoutTheGuardAndRecordsTheOverride() {
        link(TENANT, TenantApplicationStatus.SUSPENDED);
        LocalDateTime at = LocalDateTime.of(2026, 10, 25, 10, 0);

        TenantApplication saved = service.activateWithOverride(TENANT, CODE, new GoLiveOverride("sa-1", at, "Board approved", "A,B"), "sa-1");

        verify(guard, never()).requireReady(anyString(), anyString());
        assertThat(saved.getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
        assertThat(saved.getGoLiveOverrideBy()).isEqualTo("sa-1");
        assertThat(saved.getGoLiveOverrideAt()).isEqualTo(at);
        assertThat(saved.getGoLiveOverrideReason()).isEqualTo("Board approved");
        assertThat(saved.getGoLiveOverrideSteps()).isEqualTo("A,B");
        assertThat(saved.getUpdatedBy()).isEqualTo("sa-1");
    }

    @Test
    void overrideOfACancelledSubscriptionRespectsTheApplicationLimit() {
        link(TENANT, TenantApplicationStatus.CANCELLED);
        assertThat(service.activateWithOverride(TENANT, CODE, new GoLiveOverride("sa", LocalDateTime.now(clock), "reason text", "A"), "sa")
                .getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
        assertThatThrownBy(() -> service.activateWithOverride("org-none", "NOPE_APP", new GoLiveOverride("sa", LocalDateTime.now(clock), "r", "A"), "sa"))
                .isInstanceOf(UamException.class);
    }

    @Test
    void ownerTeamIsPerOrganizationLink() {
        link(TENANT, TenantApplicationStatus.ACTIVE);
        link("org-2", TenantApplicationStatus.ACTIVE);

        service.updateOwnerTeam(TENANT, CODE, List.of("head-1", "head-2"), "admin");

        assertThat(service.find(TENANT, CODE).orElseThrow().getOwnerTeamUserIds()).containsExactly("head-1", "head-2");
        assertThat(service.find("org-2", CODE).orElseThrow().getOwnerTeamUserIds()).isNull();
        service.updateOwnerTeam(TENANT, CODE, null, "admin");
        assertThat(service.find(TENANT, CODE).orElseThrow().getOwnerTeamUserIds()).isEmpty();
        assertThatThrownBy(() -> service.updateOwnerTeam("org-3", CODE, List.of(), "admin")).isInstanceOf(UamException.class);
    }

    @Test
    void alertStateIsKeptPerKindAndCanBeCleared() {
        link(TENANT, TenantApplicationStatus.ACTIVE);
        LocalDateTime at = LocalDateTime.of(2026, 10, 25, 9, 0);

        service.recordAlert(TENANT, CODE, AlertKind.BLOCKED, at, "fp-1");
        service.recordAlert(TENANT, CODE, AlertKind.REFUSED, at.plusHours(1), "fp-2");
        TenantApplication saved = service.find(TENANT, CODE).orElseThrow();
        assertThat(saved.getLastBlockedAlertAt()).isEqualTo(at);
        assertThat(saved.getLastBlockedAlertFingerprint()).isEqualTo("fp-1");
        assertThat(saved.getLastRefusedAlertAt()).isEqualTo(at.plusHours(1));
        assertThat(saved.getLastRefusedAlertFingerprint()).isEqualTo("fp-2");

        service.recordAlert(TENANT, CODE, AlertKind.BLOCKED, at, null);
        service.recordAlert(TENANT, CODE, AlertKind.REFUSED, at, null);
        assertThat(saved.getLastBlockedAlertAt()).isNull();
        assertThat(saved.getLastBlockedAlertFingerprint()).isNull();
        assertThat(saved.getLastRefusedAlertFingerprint()).isNull();
        // An unknown link is ignored.
        service.recordAlert("org-none", CODE, AlertKind.BLOCKED, at, "x");
    }

    @Test
    void activeLinksAreActiveAndNotExpiredAcrossOrganizations() {
        link(TENANT, TenantApplicationStatus.ACTIVE);
        link("org-2", TenantApplicationStatus.SUSPENDED);
        link("org-3", TenantApplicationStatus.CANCELLED);
        TenantApplication expired = link("org-4", TenantApplicationStatus.ACTIVE);
        expired.setValidUntil(LocalDate.of(2026, 10, 24));
        TenantApplication lastDay = link("org-5", TenantApplicationStatus.ACTIVE);
        lastDay.setValidUntil(LocalDate.of(2026, 10, 25));

        assertThat(service.activeLinks(CODE)).extracting(TenantApplication::getTenantId).containsExactlyInAnyOrder(TENANT, "org-5");
    }

    private final class InMemory implements TenantApplicationRepository {
        @Override
        public TenantApplication save(TenantApplication subscription) {
            if (subscription.getId() == null) {
                subscription.setId(UUID.randomUUID().toString());
            }
            if (!rows.contains(subscription)) {
                rows.add(subscription);
            }
            return subscription;
        }

        @Override
        public Optional<TenantApplication> findByTenantIdAndApplicationCode(String tenantId, String applicationCode) {
            return rows.stream().filter(r -> r.getTenantId().equals(tenantId) && r.getApplicationCode().equals(applicationCode)).findFirst();
        }

        @Override
        public List<TenantApplication> findByTenantId(String tenantId) {
            return rows.stream().filter(r -> r.getTenantId().equals(tenantId)).toList();
        }

        @Override
        public List<TenantApplication> findByApplicationCode(String applicationCode) {
            return rows.stream().filter(r -> r.getApplicationCode().equals(applicationCode)).toList();
        }
    }
}
