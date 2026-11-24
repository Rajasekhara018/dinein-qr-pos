package com.heuristiq.uam.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.repository.TenantRepository;

/** The default organization is found by its code, whatever id the store gave it (the literal "default" in Postgres, a generated id in Mongo). */
class DefaultOrganizationResolverTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final DefaultOrganizationResolver resolver = new DefaultOrganizationResolver(tenants, PlatformOrganizationProperties.defaults());

    @Test
    void bothIdShapesAreResolvedByCode() {
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("default").build()));
        assertThat(resolver.requireId()).isEqualTo("default");

        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("65f0c0ffee0000000000abcd").build()));
        assertThat(resolver.requireId()).isEqualTo("65f0c0ffee0000000000abcd");
        assertThat(resolver.findId()).contains("65f0c0ffee0000000000abcd");
    }

    @Test
    void theConfiguredCodeIsUsedNotAFixedOne() {
        DefaultOrganizationResolver custom = new DefaultOrganizationResolver(tenants, new PlatformOrganizationProperties("HQ", "Head office", false));
        when(tenants.findByCodeIgnoreCase("HQ")).thenReturn(Optional.of(Tenant.builder().id("hq-1").build()));

        assertThat(custom.requireId()).isEqualTo("hq-1");
    }

    @Test
    void aMissingOrganizationIsNotInventedFromTheLiteralId() {
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.empty());

        assertThat(resolver.findId()).isEmpty();
        assertThatThrownBy(resolver::requireId).isInstanceOfSatisfying(UamException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
