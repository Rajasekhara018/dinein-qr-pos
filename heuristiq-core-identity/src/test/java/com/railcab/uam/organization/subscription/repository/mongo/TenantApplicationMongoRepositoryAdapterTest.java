package com.heuristiq.uam.organization.subscription.repository.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;
import com.heuristiq.uam.organization.subscription.persistence.mongo.TenantApplicationDocument;

/** The Mongo adapter maps every field both ways, including the per-application lookup across organizations. */
class TenantApplicationMongoRepositoryAdapterTest {
    private final TenantApplicationMongoSpringRepository spring = mock(TenantApplicationMongoSpringRepository.class);
    private final TenantApplicationMongoRepositoryAdapter adapter = new TenantApplicationMongoRepositoryAdapter(spring);

    private static TenantApplicationDocument stored(String tenantId, TenantApplicationStatus status) {
        return TenantApplicationDocument.builder().id("id-" + tenantId).tenantId(tenantId).applicationCode("BEST_PURCHASE_APP").status(status)
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
        when(spring.save(org.mockito.ArgumentMatchers.any(TenantApplicationDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantApplication saved = adapter.save(TenantApplication.builder().id("x").tenantId("org-a").applicationCode("BEST_PURCHASE_APP")
                .status(TenantApplicationStatus.SUSPENDED).note("n").build());

        assertThat(saved.getId()).isEqualTo("x");
        assertThat(saved.getTenantId()).isEqualTo("org-a");
        assertThat(saved.getStatus()).isEqualTo(TenantApplicationStatus.SUSPENDED);
        assertThat(saved.getNote()).isEqualTo("n");
    }
}
