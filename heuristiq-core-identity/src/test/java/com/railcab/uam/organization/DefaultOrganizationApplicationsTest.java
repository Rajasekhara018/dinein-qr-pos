package com.heuristiq.uam.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.uam.application.Application;
import com.heuristiq.uam.application.ApplicationService;
import com.heuristiq.uam.application.InMemoryApplicationRepository;
import com.heuristiq.uam.application.InMemoryTenantApplicationRepository;
import com.heuristiq.uam.application.SessionOrganizationResolver;
import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationService;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.users.repository.UamUserRepository;

/**
 * On an empty database the very first start leaves the default organization holding all five catalogue applications (rows and
 * links), with no second restart needed; the organization is found by its code even though its id is generated (Mongo mode).
 */
class DefaultOrganizationApplicationsTest {
    private static final String GENERATED_ID = "6716a0c0e4b0f1a2b3c4d5e6";

    private InMemoryApplicationRepository applications;
    private InMemoryTenantApplicationRepository links;
    private ApplicationService applicationService;
    private DefaultOrganizationBootstrap bootstrap;

    @BeforeEach
    void emptyDatabase() {
        applications = new InMemoryApplicationRepository();
        links = new InMemoryTenantApplicationRepository();
        TenantRepository tenants = mock(TenantRepository.class);
        AtomicReference<Tenant> stored = new AtomicReference<>();
        when(tenants.findByCodeIgnoreCase(anyString())).thenAnswer(inv ->
                Optional.ofNullable(stored.get()).filter(t -> t.getCode().equalsIgnoreCase(inv.getArgument(0))));
        when(tenants.save(any())).thenAnswer(inv -> {
            Tenant tenant = inv.getArgument(0);
            tenant.setId(GENERATED_ID);
            stored.set(tenant);
            return tenant;
        });
        when(tenants.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get()).filter(t -> t.getId().equals(inv.getArgument(0))));

        TenantApplicationService subscriptions = new TenantApplicationService(tenants, applications, links);
        applicationService = new ApplicationService(applications, subscriptions,
                new SessionOrganizationResolver(new com.heuristiq.uam.organization.DefaultOrganizationResolver(tenants, PlatformOrganizationProperties.defaults())),
                mock(com.heuristiq.uam.application.access.repository.ApplicationAccessRepository.class),
                mock(com.heuristiq.uam.bootstrap.PermissionCatalogProvisioner.class));
        UamUserRepository users = mock(UamUserRepository.class);
        when(users.findByUserNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        bootstrap = new DefaultOrganizationBootstrap(tenants, applicationService, subscriptions, mock(TenantMembershipServiceUseCase.class),
                users, PlatformOrganizationProperties.defaults(), "admin");
    }

    @AfterEach
    void clearSession() {
        TenantContext.clear();
    }

    @Test
    void aFreshDatabaseStartsWithoutAnyApplicationBecauseTheyAreConfiguredByHand() {
        bootstrap.run();

        assertThat(DefaultOrganizationConstants.DEFAULT_ORGANIZATION_APPLICATIONS).isEmpty();
        assertThat(links.rows).isEmpty();
        assertThat(applications.rows).isEmpty();
    }

    @Test
    void theLookupIsEmptyUntilAnAdministratorCreatesAnApplication() {
        bootstrap.run();

        assertThat(applicationService.lookup().applications()).isEmpty();
        TenantContext.set(GENERATED_ID);
        assertThat(applicationService.lookup().applications()).isEmpty();
    }

    @Test
    void anApplicationAnAdministratorCreatesSurvivesARestartAndIsNotDuplicated() {
        bootstrap.run();
        TenantContext.set(GENERATED_ID);
        applicationService.ensureCatalogueApplication(ApplicationCatalogue.CENTRALISED_WALLET_APP);
        TenantContext.clear();

        bootstrap.run();

        assertThat(List.copyOf(applications.rows)).extracting(Application::getCode).containsExactly(ApplicationCatalogue.CENTRALISED_WALLET_APP);
        assertThat(links.rows).isEmpty();
    }
}
