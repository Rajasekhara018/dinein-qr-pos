package com.heuristiq.identity.organization.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.RegistrationResponse;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.registration.UserRegistrationServiceUseCase;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

class OrganizationAdminServiceTest {

    private TenantRepository tenants;
    private UamRoleRepository roles;
    private UamUserRepository users;
    private UserRegistrationServiceUseCase registration;
    private UserCredentialServiceUseCase credentials;
    private com.heuristiq.identity.organization.subscription.repository.TenantApplicationRepository subscriptions;
    private com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase access;
    private TenantMembershipServiceUseCase memberships;
    private OrganizationAdminService service;
    private final CreateOrganizationAdminRequest request = new CreateOrganizationAdminRequest("Asha Rao", "9876543210", "asha@acme.com");

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        roles = mock(UamRoleRepository.class);
        users = mock(UamUserRepository.class);
        registration = mock(UserRegistrationServiceUseCase.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        subscriptions = mock(com.heuristiq.identity.organization.subscription.repository.TenantApplicationRepository.class);
        access = mock(com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase.class);
        when(subscriptions.findByTenantId(any())).thenReturn(List.of());
        memberships = mock(TenantMembershipServiceUseCase.class);
        service = new OrganizationAdminService(tenants, roles, users, registration, credentials, memberships, mock(IdentityProviderSyncService.class),
                mock(com.heuristiq.identity.notification.NmsNotificationService.class),
                new com.heuristiq.identity.bootstrap.PermissionCatalogLoader(new ObjectMapper()),
                subscriptions, access, "https://realboom.example/auth/login");
        Tenant tenant = new Tenant();
        tenant.setId("t-acme");
        tenant.setCode("acme");
        tenant.setName("Acme");
        when(tenants.findById("t-acme")).thenReturn(Optional.of(tenant));
        when(roles.findByCodeIgnoreCase(any())).thenReturn(Optional.empty());
        when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private UamUser account(String id) {
        UamUser u = new UamUser();
        u.setId(id);
        u.setEmail("asha@acme.com");
        u.setStatus(UamEnums.UserStatus.ACTIVE);
        u.setRoles(List.of("OPS"));
        return u;
    }

    @Test
    void aNewAccountGetsTheOrganizationsAdminRoleMembershipAndAOneTimePassword() {
        UamUser created = account("u1");
        when(users.findByPhone(any())).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(registration.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(
                new RegistrationResponse("ok", "u1", null, null, null, null, null)));
        when(users.findById("u1")).thenReturn(Optional.of(created));

        OrganizationAdminResult result = service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("t-acme", request));

        assertThat(result.accountCreated()).isTrue();
        assertThat(result.roleCode()).isEqualTo("ACME_ADMIN");
        assertThat(result.temporaryPassword()).hasSize(OrganizationAdminConstants.TEMPORARY_PASSWORD_LENGTH);
        assertThat(result.toString()).doesNotContain(result.temporaryPassword());
        assertThat(created.getRoles()).containsExactly("ACME_ADMIN");
        assertThat(created.getTenantId()).isEqualTo("t-acme");
        ArgumentCaptor<UamRole> role = ArgumentCaptor.forClass(UamRole.class);
        verify(roles).save(role.capture());
        assertThat(role.getValue().getTenantId()).as("the role belongs to the organization").isEqualTo("t-acme");
        verify(memberships).addMember(any());
    }

    @Test
    void anExistingAccountKeepsItsPasswordAndItsOtherRoles() {
        UamUser existing = account("u9");
        when(users.findByPhone(any())).thenReturn(Optional.of(existing));
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.of(existing));

        OrganizationAdminResult result = service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("t-acme", request));

        assertThat(result.accountCreated()).isFalse();
        assertThat(result.temporaryPassword()).isNull();
        assertThat(existing.getRoles()).containsExactly("OPS", "ACME_ADMIN");
        verify(credentials, never()).setPassword(any());
    }

    @Test
    void aRoleOfAnotherOrganizationWithTheSameCodeIsRefused() {
        UamRole other = UamRole.builder().code("ACME_ADMIN").tenantId("t-other").build();
        when(roles.findByCodeIgnoreCase("ACME_ADMIN")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("t-acme", request)))
                .isInstanceOf(UamException.class);
    }

    @Test
    void anUnknownOrganizationIsNotFound() {
        assertThatThrownBy(() -> service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("nope", request)))
                .isInstanceOf(UamException.class);
    }

    @Test
    void theAdminRoleGetsEveryPrivilegeExceptThePlatformOnlyOnes() {
        UamUser created = account("u1");
        when(users.findByPhone(any())).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(registration.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(
                new RegistrationResponse("ok", "u1", null, null, null, null, null)));
        when(users.findById("u1")).thenReturn(Optional.of(created));

        service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("t-acme", request));

        ArgumentCaptor<UamRole> role = ArgumentCaptor.forClass(UamRole.class);
        verify(roles).save(role.capture());
        assertThat(role.getValue().getPermissions())
                .contains("USER_CREATE", "ORG_DRAFT_PUBLISH", "MATRIMONY_AGENT_CUSTOMER_FEE_CREATE", "WALLET_ADMIN_OVERVIEW_READ")
                .doesNotContain("ORGANIZATION_CREATE", "ORGANIZATION_ADMIN_CREATE", "APPLICATION_DELETE", "CLIENT_CREATE", "UAM_SEARCH_REINDEX_CREATE")
                .hasSizeGreaterThan(300);
    }

    @Test
    void anOrganizationAdminCanViewApplicationsAndUpdateTheirOrganizationByDefault() {
        assertThat(OrganizationAdminConstants.administratorPermissions(List.of("APPLICATION_CREATE", "APPLICATION_UPDATE",
                "APPLICATION_DELETE", "ORGANIZATION_APPLICATION_SUBSCRIBE", "APPLICATION_LIST", "APPLICATION_READ",
                "ORGANIZATION_LIST", "ORGANIZATION_READ", "ORGANIZATION_UPDATE", "ORGANIZATION_DELETE")))
                .containsExactly("APPLICATION_CREATE", "APPLICATION_LIST", "APPLICATION_READ",
                        "ORGANIZATION_LIST", "ORGANIZATION_READ", "ORGANIZATION_UPDATE");
    }

    @Test
    void theAdminRoleCanSignInToEveryApplicationTheOrganizationHas() {
        var subscription = com.heuristiq.identity.organization.subscription.TenantApplication.builder()
                .tenantId("t-acme").applicationCode("MATRIMONY").status(com.heuristiq.identity.organization.subscription.TenantApplicationStatus.ACTIVE).build();
        when(subscriptions.findByTenantId("t-acme")).thenReturn(List.of(subscription));
        UamUser existing = account("u9");
        when(users.findByPhone(any())).thenReturn(Optional.of(existing));
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.of(existing));

        service.provision(new OrganizationAdminServiceUseCase.ProvisionInput("t-acme", request));

        verify(access).grant(any(com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase.GrantInput.class));
    }
}
