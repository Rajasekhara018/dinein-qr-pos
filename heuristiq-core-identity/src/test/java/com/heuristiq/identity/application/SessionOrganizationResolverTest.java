package com.heuristiq.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.identity.common.UamException;

import com.heuristiq.identity.organization.DefaultOrganizationResolver;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.security.TenantContext;

/** The session organization is the token's; without one (and no signed-in person), the platform organization by code. */
class SessionOrganizationResolverTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final SessionOrganizationResolver resolver = new SessionOrganizationResolver(new DefaultOrganizationResolver(tenants, PlatformOrganizationProperties.defaults()));

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void theTokensOrganizationWins() {
        TenantContext.set("org-b");

        assertThat(resolver.sessionOrganizationId()).isEqualTo("org-b");
        verifyNoInteractions(tenants);
    }

    @Test
    void withoutOneThePlatformOrganizationIsUsed() {
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("uuid-1").build()));

        assertThat(resolver.sessionOrganizationId()).isEqualTo("uuid-1");
    }

    @Test
    void aGeneratedIdDefaultOrganizationIsFoundByCodeNotAssumedToBeCalledDefault() {
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("65f0c0ffee0000000000abcd").build()));
        TenantContext.set(" ");

        assertThat(resolver.sessionOrganizationId()).isEqualTo("65f0c0ffee0000000000abcd");
    }

    @Test
    void aMissingDefaultOrganizationIsAnErrorNeverTheLiteralDefaultId() {
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::sessionOrganizationId)
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void aSignedInCallerWithoutAnOrganizationClaimIsRefusedAndTheDefaultIsNotEvenLookedUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(resolver::sessionOrganizationId)
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(tenants);
    }

    @Test
    void anInternalServiceCallWithoutAnOrganizationUsesTheDefaultOrganization() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("svc", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        when(tenants.findByCodeIgnoreCase("DEFAULT")).thenReturn(Optional.of(Tenant.builder().id("uuid-2").build()));

        assertThat(resolver.sessionOrganizationId()).isEqualTo("uuid-2");
    }
}
