package com.heuristiq.uam.organization;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.LanguageEnum;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.organization.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An organization's currency, country, time zone and default language: real codes only, stored normalized, and an update that
 * leaves one out keeps it.
 */
class TenantRegionalSettingsTest {

    private TenantRepository tenants;
    private TenantService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        when(tenants.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tenants.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        service = new TenantService(tenants, mock(IdentityProviderSyncService.class));
    }

    private static UpsertOrganizationRequest request(String currency, String country, String timeZone, LanguageEnum language) {
        return new UpsertOrganizationRequest("ACME", "Acme", null, null, null, null, null, null, null, null, null,
                currency, country, timeZone, language);
    }

    @Test
    void storesTheSettingsNormalizedOnCreate() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request(" usd ", "ae", "Asia/Dubai", LanguageEnum.AR))).tenant();

        assertThat(created.getCurrency()).isEqualTo("USD");
        assertThat(created.getCountry()).isEqualTo("AE");
        assertThat(created.getTimeZone()).isEqualTo("Asia/Dubai");
        assertThat(created.getDefaultLanguage()).isEqualTo(LanguageEnum.AR);
    }

    @Test
    void leavesThemUnsetWhenNotGiven() {
        Tenant created = service.create(new TenantServiceUseCase.CreateInput(request("", null, " ", null))).tenant();

        assertThat(created.getCurrency()).isNull();
        assertThat(created.getCountry()).isNull();
        assertThat(created.getTimeZone()).isNull();
        assertThat(created.getDefaultLanguage()).isNull();
    }

    @Test
    void anUpdateChangesTheGivenOnesAndKeepsTheOnesLeftOut() {
        Tenant existing = Tenant.builder().id("t1").code("ACME").name("Acme").currency("INR").country("IN").timeZone("Asia/Kolkata")
                .defaultLanguage(LanguageEnum.EN).build();
        when(tenants.findById("t1")).thenReturn(Optional.of(existing));

        Tenant changed = service.update(new TenantServiceUseCase.UpdateInput("t1", request("aed", null, null, LanguageEnum.TE))).tenant();

        assertThat(changed.getCurrency()).isEqualTo("AED");
        assertThat(changed.getCountry()).as("left out: kept").isEqualTo("IN");
        assertThat(changed.getTimeZone()).isEqualTo("Asia/Kolkata");
        assertThat(changed.getDefaultLanguage()).isEqualTo(LanguageEnum.TE);
    }

    @Test
    void refusesCodesThatAreNotRealBeforeSavingAnything() {
        assertThatThrownBy(() -> service.create(new TenantServiceUseCase.CreateInput(request("XYZ", null, null, null))))
                .isInstanceOf(UamException.class).hasMessageContaining("currency");
        assertThatThrownBy(() -> service.create(new TenantServiceUseCase.CreateInput(request(null, "ZZ", null, null))))
                .isInstanceOf(UamException.class).hasMessageContaining("country");
        assertThatThrownBy(() -> service.create(new TenantServiceUseCase.CreateInput(request(null, null, "Mars/Base", null))))
                .isInstanceOf(UamException.class).hasMessageContaining("timeZone");
        assertThatThrownBy(() -> service.create(new TenantServiceUseCase.CreateInput(request(null, null, "+05:30", null))))
                .as("an offset is not a region").isInstanceOf(UamException.class);
        verify(tenants, never()).save(any());
    }
}
