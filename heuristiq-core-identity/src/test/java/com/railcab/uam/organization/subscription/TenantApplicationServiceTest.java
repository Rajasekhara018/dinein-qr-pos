package com.heuristiq.uam.organization.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.uam.application.Application;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;

/** Which applications an organization may use: subscribing, the application limit, cancelling and re-subscribing. */
class TenantApplicationServiceTest {
    private static final String TENANT = "org-1";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
    private TenantRepository tenants;
    private ApplicationRepository applications;
    private InMemorySubscriptions subscriptions;
    private TenantApplicationService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        applications = mock(ApplicationRepository.class);
        subscriptions = new InMemorySubscriptions();
        service = new TenantApplicationService(tenants, applications, subscriptions, clock);
        when(tenants.findById(TENANT)).thenReturn(Optional.of(Tenant.builder().id(TENANT).status(TenantStatus.ACTIVE).build()));
        register("MATRIMONY", true);
        register("RIDER", true);
        register("OLD", false);
    }

    @Test
    void subscribingGivesTheOrganizationTheApplication() {
        TenantApplication subscription = subscribe("matrimony", 500).subscription();

        assertThat(subscription.getApplicationCode()).isEqualTo("MATRIMONY");
        assertThat(subscription.getStatus()).isEqualTo(TenantApplicationStatus.ACTIVE);
        assertThat(subscription.getMaxUsers()).isEqualTo(500);
        assertThat(subscription.getSubscribedBy()).isEqualTo("admin-1");
    }

    @Test
    void subscribingAgainChangesNothing() {
        TenantApplication first = subscribe("MATRIMONY", 500).subscription();

        TenantApplication again = subscribe("MATRIMONY", 10).subscription();

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(again.getMaxUsers()).isEqualTo(500);
        assertThat(subscriptions.rows).hasSize(1);
    }

    @Test
    void anUnknownOrSwitchedOffApplicationCannotBeSubscribed() {
        when(applications.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscribe("NOPE", null)).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> subscribe("OLD", null)).isInstanceOf(UamException.class);
        assertThat(subscriptions.rows).isEmpty();
    }

    @Test
    void anOrganizationCannotHoldMoreApplicationsThanItsLimit() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT).status(TenantStatus.ACTIVE).maxApplications(1).build()));
        subscribe("MATRIMONY", null);

        assertThatThrownBy(() -> subscribe("RIDER", null)).isInstanceOf(UamException.class).hasMessageContaining("limit of 1");
    }

    @Test
    void cancellingFreesTheSlotAndTheApplicationCanComeBack() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT).status(TenantStatus.ACTIVE).maxApplications(1).build()));
        subscribe("MATRIMONY", null);

        TenantApplication cancelled = service.cancel(new TenantApplicationServiceUseCase.CancelInput(TENANT, "matrimony", "admin-1")).subscription();
        assertThat(cancelled.getStatus()).isEqualTo(TenantApplicationStatus.CANCELLED);
        subscribe("RIDER", null);

        assertThatThrownBy(() -> subscribe("MATRIMONY", null)).isInstanceOf(UamException.class);
        assertThat(subscriptions.rows).hasSize(2);
    }

    @Test
    void anArchivedOrganizationCannotBeGivenApplications() {
        when(tenants.findById(TENANT)).thenReturn(Optional.of(Tenant.builder().id(TENANT).status(TenantStatus.ARCHIVED).build()));

        assertThatThrownBy(() -> subscribe("MATRIMONY", null)).isInstanceOf(UamException.class);
    }

    @Test
    void theListNamesEachApplicationAndKeepsCancelledOnes() {
        subscribe("MATRIMONY", null);
        subscribe("RIDER", null);
        service.cancel(new TenantApplicationServiceUseCase.CancelInput(TENANT, "RIDER", "admin-1"));

        List<TenantApplicationView> views = service.list(new TenantApplicationServiceUseCase.ListInput(TENANT)).subscriptions();

        assertThat(views).extracting(v -> v.subscription().getApplicationCode() + ":" + v.subscription().getStatus() + ":" + v.applicationName())
                .containsExactlyInAnyOrder("MATRIMONY:ACTIVE:MATRIMONY", "RIDER:CANCELLED:RIDER");
    }

    @Test
    void updateChangesOnlyWhatIsGiven() {
        subscribe("MATRIMONY", 500);

        TenantApplication updated = service.update(new TenantApplicationServiceUseCase.UpdateInput(TENANT, "MATRIMONY",
                new UpdateTenantApplicationRequest(TenantApplicationStatus.SUSPENDED, null, null, null), "admin-2")).subscription();

        assertThat(updated.getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
        assertThat(updated.getMaxUsers()).isEqualTo(500);
        assertThat(updated.getUpdatedBy()).isEqualTo("admin-2");
    }

    @Test
    void ensureSubscribedAddsTheApplicationOnceAndIgnoresTheLimit() {
        service.ensureSubscribed(TENANT, "matrimony", "system");
        service.ensureSubscribed(TENANT, "MATRIMONY", "system");

        assertThat(subscriptions.rows).hasSize(1);
        assertThat(subscriptions.rows.get(0).getSubscribedBy()).isEqualTo("system");
    }

    @Test
    void linkedCodesSkipCancelledLinksAndKeepSuspendedOnes() {
        subscribe("RIDER", null);
        subscribe("MATRIMONY", null);
        service.update(new TenantApplicationServiceUseCase.UpdateInput(TENANT, "RIDER",
                new UpdateTenantApplicationRequest(TenantApplicationStatus.SUSPENDED, null, null, null), "admin-1"));
        service.ensureSubscribed("org-2", "OLD", "system");
        service.cancel(new TenantApplicationServiceUseCase.CancelInput(TENANT, "MATRIMONY", "admin-1"));

        assertThat(service.linkedApplicationCodes(TENANT)).containsExactly("RIDER");
        assertThat(service.linkedApplicationCodes("org-2")).containsExactly("OLD");
        assertThat(service.linkedApplicationCodes("org-unknown")).isEmpty();
    }

    @Test
    void linkedElsewhereLooksOnlyAtOtherOrganizationsLiveLinks() {
        subscribe("MATRIMONY", null);
        assertThat(service.linkedElsewhere("matrimony", TENANT)).isFalse();

        service.ensureSubscribed("org-2", "MATRIMONY", "system");
        assertThat(service.linkedElsewhere("MATRIMONY", TENANT)).isTrue();

        subscriptions.rows.stream().filter(r -> r.getTenantId().equals("org-2")).forEach(r -> r.setStatus(TenantApplicationStatus.CANCELLED));
        assertThat(service.linkedElsewhere("MATRIMONY", TENANT)).isFalse();
    }

    private TenantApplicationServiceUseCase.SubscribeOutput subscribe(String code, Integer maxUsers) {
        return service.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(TENANT,
                new SubscribeTenantApplicationRequest(code, maxUsers, null, null), "admin-1"));
    }

    private void register(String code, boolean active) {
        when(applications.findByCodeIgnoreCase(code))
                .thenReturn(Optional.of(Application.builder().code(code).name(code).active(active).build()));
    }

    private static final class InMemorySubscriptions implements TenantApplicationRepository {
        final List<TenantApplication> rows = new ArrayList<>();

        @Override
        public TenantApplication save(TenantApplication subscription) {
            if (subscription.getId() == null) {
                subscription.setId(UUID.randomUUID().toString());
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
