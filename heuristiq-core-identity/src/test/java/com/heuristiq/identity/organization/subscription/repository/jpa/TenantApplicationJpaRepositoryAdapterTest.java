package com.heuristiq.identity.organization.subscription.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.organization.subscription.persistence.jpa.TenantApplicationEntity;

/** The Jpa adapter maps every field both ways, including the per-application lookup across organizations. */
class TenantApplicationJpaRepositoryAdapterTest {
    private final TenantApplicationJpaSpringRepository spring = mock(TenantApplicationJpaSpringRepository.class);
    private final TenantApplicationJpaRepositoryAdapter adapter = new TenantApplicationJpaRepositoryAdapter(spring);

    private static TenantApplicationEntity stored(String tenantId, TenantApplicationStatus status) {
        return TenantApplicationEntity.builder().id("id-" + tenantId).tenantId(tenantId).applicationCode("BEST_PURCHASE_APP").status(status)
                .maxUsers(5).validUntil(LocalDate.of(2027, 1, 1)).note("n").subscribedBy("a").updatedBy("b").build();
    }

    @Test
    void findByApplicationCodeReturnsEveryOrganizationsLink() {
        when(spring.findByApplicationCode("BEST_PURCHASE_APP")).thenReturn(List.of(
                stored("org-a", TenantApplicationStatus.ACTIVE), stored("org-b", TenantApplicationStatus.CANCELLED)));

        List<TenantApplication> links = adapter.findByApplicationCode("BEST_PURCHASE_APP");

        assertThat(links).extracting(TenantApplication::getTenantId).containsExactly("org-a", "org-b");
        assertThat(links.get(1).getStatus()).isEqualTo(TenantApplicationStatus.CANCELLED);
        assertThat(links.get(0).getMaxUsers()).isEqualTo(5);
        assertThat(links.get(0).getValidUntil()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void findByTenantIdIsScopedToTheOrganization() {
        when(spring.findByTenantId("org-a")).thenReturn(List.of(stored("org-a", TenantApplicationStatus.ACTIVE)));
        when(spring.findByTenantId("org-b")).thenReturn(List.of());
        when(spring.findByTenantIdAndApplicationCode("org-b", "BEST_PURCHASE_APP")).thenReturn(Optional.empty());

        assertThat(adapter.findByTenantId("org-a")).extracting(TenantApplication::getApplicationCode).containsExactly("BEST_PURCHASE_APP");
        assertThat(adapter.findByTenantId("org-b")).isEmpty();
        assertThat(adapter.findByTenantIdAndApplicationCode("org-b", "BEST_PURCHASE_APP")).isEmpty();
    }

    @Test
    void saveRoundTripsTheLink() {
        when(spring.save(org.mockito.ArgumentMatchers.any(TenantApplicationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantApplication saved = adapter.save(TenantApplication.builder().id("x").tenantId("org-a").applicationCode("BEST_PURCHASE_APP")
                .status(TenantApplicationStatus.SUSPENDED).note("n").build());

        assertThat(saved.getId()).isEqualTo("x");
        assertThat(saved.getTenantId()).isEqualTo("org-a");
        assertThat(saved.getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
        assertThat(saved.getNote()).isEqualTo("n");
    }
}
