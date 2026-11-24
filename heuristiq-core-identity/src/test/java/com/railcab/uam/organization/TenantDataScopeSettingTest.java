package com.heuristiq.uam.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.organization.repository.TenantRepository;

/** An organization chooses how much of it people see; it defaults to all of it and is kept with the organization's other settings. */
class TenantDataScopeSettingTest {

    private TenantRepository tenants;
    private TenantService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        when(tenants.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tenants.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        service = new TenantService(tenants, mock(IdentityProviderSyncService.class));
    }

    private UpsertOrganizationRequest request(String dataScope) {
        return new UpsertOrganizationRequest("ACME", "Acme", null, null, null, null, null, dataScope, null, null, null);
    }

    @Test
    void aNewOrganizationSeesTheWholeOrganizationUnlessItChoosesOtherwise() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request(null))).tenant();

        assertThat(OrganizationSettings.dataScopeOf(created.getSettings())).isEqualTo(DataScopeMode.ORGANIZATION);
    }

    @Test
    void theChoiceIsKept() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request("DEPARTMENT"))).tenant();

        assertThat(OrganizationSettings.dataScopeOf(created.getSettings())).isEqualTo(DataScopeMode.DEPARTMENT);
    }

    @Test
    void anUpdateWithoutAChoiceKeepsTheCurrentOneAndTheOtherSettings() {
        Tenant existing = Tenant.builder().id("t1").code("ACME").name("Acme")
                .settings(Map.of("dataScope", "DEPARTMENT", "other", "kept")).build();
        when(tenants.findById("t1")).thenReturn(Optional.of(existing));

        Tenant updated = service.update(new TenantServiceUseCase.UpdateInput("t1", request(null))).tenant();

        assertThat(OrganizationSettings.dataScopeOf(updated.getSettings())).isEqualTo(DataScopeMode.DEPARTMENT);
        assertThat(updated.getSettings()).containsEntry("other", "kept");
    }

    @Test
    void anUnknownValueFallsBackToTheWholeOrganization() {
        assertThat(OrganizationSettings.dataScopeOf(Map.of("dataScope", "SOMETHING"))).isEqualTo(DataScopeMode.ORGANIZATION);
        assertThat(OrganizationSettings.dataScopeOf(null)).isEqualTo(DataScopeMode.ORGANIZATION);
    }
}
