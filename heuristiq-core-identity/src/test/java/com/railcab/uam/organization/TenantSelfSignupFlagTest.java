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

/** The self-signup opt-in is off by default, set through create/update, and an update that omits it keeps it. */
class TenantSelfSignupFlagTest {

    private TenantRepository tenants;
    private TenantService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        when(tenants.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tenants.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        service = new TenantService(tenants, mock(IdentityProviderSyncService.class));
    }

    private UpsertOrganizationRequest request(Boolean open) {
        return new UpsertOrganizationRequest("ACME", "Acme", null, null, null, null, null, null, null, null, open);
    }

    @Test
    void offByDefault() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request(null))).tenant();

        assertThat(OrganizationSettings.openForSelfSignup(created.getSettings())).isFalse();
        assertThat(OrganizationSettings.openForSelfSignup(null)).isFalse();
        assertThat(OrganizationSettings.openForSelfSignup(Map.of("openForSelfSignup", "garbage"))).isFalse();
    }

    @Test
    void createCanOptIn() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request(true))).tenant();

        assertThat(OrganizationSettings.openForSelfSignup(created.getSettings())).isTrue();
        assertThat(OrganizationSettings.dataScopeOf(created.getSettings())).isEqualTo(DataScopeMode.ORGANIZATION);
    }

    @Test
    void updateTogglesItAndKeepsOtherSettingsAndAnOmittedValueKeepsIt() {
        Tenant existing = Tenant.builder().id("t1").code("ACME").name("Acme")
                .settings(Map.of("dataScope", "DEPARTMENT")).build();
        when(tenants.findById("t1")).thenReturn(Optional.of(existing));

        Tenant on = service.update(new TenantServiceUseCase.UpdateInput("t1", request(true))).tenant();
        assertThat(OrganizationSettings.openForSelfSignup(on.getSettings())).isTrue();
        assertThat(on.getSettings()).containsEntry("dataScope", "DEPARTMENT");

        Tenant kept = service.update(new TenantServiceUseCase.UpdateInput("t1", request(null))).tenant();
        assertThat(OrganizationSettings.openForSelfSignup(kept.getSettings())).isTrue();

        Tenant off = service.update(new TenantServiceUseCase.UpdateInput("t1", request(false))).tenant();
        assertThat(OrganizationSettings.openForSelfSignup(off.getSettings())).isFalse();
    }
}
