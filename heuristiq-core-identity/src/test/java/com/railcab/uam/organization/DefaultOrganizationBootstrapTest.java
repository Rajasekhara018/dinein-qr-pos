package com.heuristiq.uam.organization;

import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The platform organization comes from configuration, owns the bootstrap admin, and adopts unowned accounts only when asked. */
class DefaultOrganizationBootstrapTest {

    private static final PlatformOrganizationProperties REALBOOM = new PlatformOrganizationProperties("REALBOOM", "Realboom Organization", false);

    private TenantRepository tenants;
    private UamUserRepository users;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        users = mock(UamUserRepository.class);
        when(tenants.save(any())).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId("org-1");
            return tenant;
        });
    }

    private DefaultOrganizationBootstrap bootstrap(PlatformOrganizationProperties properties) {
        return new DefaultOrganizationBootstrap(tenants, mock(com.heuristiq.uam.application.ApplicationServiceUseCase.class),
                mock(com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase.class), mock(TenantMembershipServiceUseCase.class), users, properties, "admin");
    }

    private static UamUser user(String id, String tenantId) {
        return UamUser.builder().id(id).userName("user-" + id).tenantId(tenantId).build();
    }

    @Test
    void createsThePlatformOrganizationWithTheConfiguredCodeAndName() {
        when(tenants.findByCodeIgnoreCase("REALBOOM")).thenReturn(Optional.empty());

        bootstrap(REALBOOM).run();

        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenants).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("REALBOOM");
        assertThat(saved.getValue().getName()).isEqualTo("Realboom Organization");
    }

    @Test
    void reusesTheExistingOrganizationInsteadOfCreatingAnotherOne() {
        Tenant existing = Tenant.builder().id("uuid-7").code("realboom").build();
        when(tenants.findByCodeIgnoreCase("REALBOOM")).thenReturn(Optional.of(existing));

        bootstrap(REALBOOM).run();

        verify(tenants, never()).save(any());
    }

    @Test
    void theBootstrapAdminIsPlacedInThePlatformOrganizationButNeverMovedFromAnother() {
        when(tenants.findByCodeIgnoreCase("REALBOOM")).thenReturn(Optional.of(Tenant.builder().id("uuid-7").code("REALBOOM").build()));
        UamUser unplaced = user("a", null);
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.of(unplaced));

        bootstrap(REALBOOM).run();

        assertThat(unplaced.getTenantId()).isEqualTo("uuid-7");
        verify(users).save(unplaced);

        UamUser placed = user("b", "other-org");
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.of(placed));
        bootstrap(REALBOOM).run();

        assertThat(placed.getTenantId()).isEqualTo("other-org");
    }

    @Test
    void accountsWithoutAnOrganizationAreLeftAloneUnlessAdoptionIsSwitchedOn() {
        when(tenants.findByCodeIgnoreCase("REALBOOM")).thenReturn(Optional.of(Tenant.builder().id("uuid-7").code("REALBOOM").build()));
        when(users.findByUserNameIgnoreCase("admin")).thenReturn(Optional.empty());
        UamUser unowned = user("u1", null);
        UamUser owned = user("u2", "acme");
        when(users.findAll(any(Pageable.class))).thenAnswer(invocation -> new PageImpl<>(List.of(unowned, owned), invocation.getArgument(0), 2));

        bootstrap(REALBOOM).run();
        assertThat(unowned.getTenantId()).isNull();

        bootstrap(new PlatformOrganizationProperties("REALBOOM", "Realboom Organization", true)).run();

        assertThat(unowned.getTenantId()).isEqualTo("uuid-7");
        assertThat(owned.getTenantId()).isEqualTo("acme");
        verify(users).save(unowned);
        verify(users, never()).save(owned);
    }
}
