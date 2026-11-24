package com.heuristiq.uam.application.client;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The organization of an API client is the token's for an organization's admin, and an existing one for a platform administrator. */
class OrganizationClientTenantResolverTest {

    private TenantRepository tenants;
    private OrganizationClientTenantResolver resolver;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        when(tenants.findById("acme")).thenReturn(Optional.of(Tenant.builder().id("acme").code("ACME").build()));
        when(tenants.findById("other")).thenReturn(Optional.of(Tenant.builder().id("other").code("OTHER").build()));
        when(tenants.findById("default")).thenReturn(Optional.empty());
        when(tenants.findById("ACME")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("ACME")).thenReturn(Optional.of(Tenant.builder().id("acme").code("ACME").build()));
        when(tenants.findById("made-up")).thenReturn(Optional.empty());
        when(tenants.findByCodeIgnoreCase("made-up")).thenReturn(Optional.empty());
        resolver = new OrganizationClientTenantResolver(tenants);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void signIn(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "x", authority));
    }

    @Test
    void aPlatformAdministratorNamesAnExistingOrganizationByIdOrCodeOrNone() {
        signIn("ROLE_SUPER_ADMIN");

        assertThat(resolver.resolve("acme")).isEqualTo("acme");
        assertThat(resolver.resolve(" ACME ")).isEqualTo("acme");
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("  ")).isNull();
    }

    @Test
    void anOrganizationThatDoesNotExistIsRefused() {
        signIn("ROLE_SUPER_ADMIN");

        assertThatThrownBy(() -> resolver.resolve("made-up")).isInstanceOf(UamException.class)
                .hasMessageContaining("Organization not found")
                .satisfies(e -> assertThat(((UamException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void anOrganizationAdminAlwaysGetsTheTokensOrganization() {
        signIn("ROLE_ACME_ADMIN");
        TenantContext.set("acme");

        assertThat(resolver.resolve(null)).isEqualTo("acme");
        assertThat(resolver.resolve("")).isEqualTo("acme");
        assertThat(resolver.resolve("acme")).isEqualTo("acme");
        assertThat(resolver.resolve("ACME")).isEqualTo("acme");
    }

    @Test
    void anOrganizationAdminCannotCreateForAnotherOrganizationOrAnInventedOne() {
        signIn("ROLE_ACME_ADMIN");
        TenantContext.set("acme");

        assertThatThrownBy(() -> resolver.resolve("other")).isInstanceOf(UamException.class)
                .satisfies(e -> assertThat(((UamException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> resolver.resolve("made-up")).isInstanceOf(UamException.class)
                .hasMessageContaining("Organization not found");
    }

    @Test
    void aCallerWithoutAnOrganizationGetsNoneWhenNothingIsNamed() {
        signIn("ROLE_ACME_ADMIN");

        assertThat(resolver.resolve(null)).isNull();
    }
}
