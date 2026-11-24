package com.heuristiq.uam.organization;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.enums.UamEnums.TenantType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.organization.TenantServiceUseCase.CreateInput;
import com.heuristiq.uam.organization.TenantServiceUseCase.DeleteInput;
import com.heuristiq.uam.organization.TenantServiceUseCase.GetInput;
import com.heuristiq.uam.organization.TenantServiceUseCase.ListInput;
import com.heuristiq.uam.organization.TenantServiceUseCase.ListPageInput;
import com.heuristiq.uam.organization.TenantServiceUseCase.UpdateInput;
import com.heuristiq.uam.organization.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {
    private TenantRepository repository;
    private IdentityProviderSyncService idp;
    private TenantService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        when(repository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new TenantService(repository, idp);
    }

    private static UpsertOrganizationRequest request(String code, String name, TenantType type, TenantStatus status, String scope) {
        return new UpsertOrganizationRequest(code, name, " desc ", type, status, " s@x.com ", " 1800 ", scope, 10, 5, null);
    }

    @Test
    void createNormalizesCodeNameAndDefaultsStatusAndScope() {
        Tenant tenant = service.create(new CreateInput(request(" acme ", " ", null, null, null))).tenant();

        assertThat(tenant.getCode()).isEqualTo("ACME");
        assertThat(tenant.getName()).isEqualTo("ACME");
        assertThat(tenant.getDescription()).isEqualTo("desc");
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getSupportEmail()).isEqualTo("s@x.com");
        assertThat(tenant.getSupportPhone()).isEqualTo("1800");
        assertThat(tenant.getMaxUsers()).isEqualTo(10);
        assertThat(tenant.getSettings()).containsEntry(OrganizationConstants.DATA_SCOPE_KEY, DataScopeMode.ORGANIZATION.name());
        verify(idp).syncOrganization(tenant);
    }

    @Test
    void createKeepsAnExplicitStatusTypeNameAndDepartmentScope() {
        Tenant tenant = service.create(new CreateInput(request("beta", " Beta Co ", TenantType.PARTNER, TenantStatus.SUSPENDED, " department "))).tenant();

        assertThat(tenant.getName()).isEqualTo("Beta Co");
        assertThat(tenant.getType()).isEqualTo(TenantType.PARTNER);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.getSettings()).containsEntry(OrganizationConstants.DATA_SCOPE_KEY, "DEPARTMENT");
    }

    @Test
    void createRejectsBlankAndDuplicateCodes() {
        assertThatThrownBy(() -> service.create(new CreateInput(request(" ", null, null, null, null))))
                .hasMessageContaining("code is required");
        when(repository.findByCodeIgnoreCase("DUP")).thenReturn(Optional.of(Tenant.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(request("dup", null, null, null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
        verify(idp, never()).syncOrganization(any());
    }

    @Test
    void updateKeepsTypeAndStatusWhenOmittedAndOtherSettingsWhenScopeOmitted() {
        Tenant existing = Tenant.builder().id("t1").code("OLD").type(TenantType.CUSTOMER).status(TenantStatus.INACTIVE)
                .settings(new HashMap<>(Map.of("theme", "dark", OrganizationConstants.DATA_SCOPE_KEY, "DEPARTMENT")))
                .audit(AuditInfo.builder().build()).build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));
        when(repository.findByCodeIgnoreCase("NEW")).thenReturn(Optional.of(existing));

        Tenant out = service.update(new UpdateInput("t1", request("new", "New", null, null, null))).tenant();

        assertThat(out.getCode()).isEqualTo("NEW");
        assertThat(out.getType()).isEqualTo(TenantType.CUSTOMER);
        assertThat(out.getStatus()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(out.getSettings()).containsEntry("theme", "dark").containsEntry(OrganizationConstants.DATA_SCOPE_KEY, "DEPARTMENT");
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
        verify(idp).syncOrganization(out);
    }

    @Test
    void updateAppliesTypeStatusAndScopeWhenGiven() {
        Tenant existing = Tenant.builder().id("t1").code("OLD").build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));

        Tenant out = service.update(new UpdateInput("t1", request("new", "New", TenantType.VENDOR, TenantStatus.ARCHIVED, "ORGANIZATION"))).tenant();

        assertThat(out.getType()).isEqualTo(TenantType.VENDOR);
        assertThat(out.getStatus()).isEqualTo(TenantStatus.ARCHIVED);
        assertThat(out.getSettings()).containsEntry(OrganizationConstants.DATA_SCOPE_KEY, "ORGANIZATION");
    }

    @Test
    void updateRejectsAnotherOrganizationsCodeAndUnknownIds() {
        Tenant existing = Tenant.builder().id("t1").build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));
        when(repository.findByCodeIgnoreCase("TAKEN")).thenReturn(Optional.of(Tenant.builder().id("t2").build()));
        assertThatThrownBy(() -> service.update(new UpdateInput("t1", request("taken", null, null, null, null))))
                .hasMessageContaining("already exists");

        when(repository.findById("none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(new UpdateInput("none", request("x", null, null, null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listVariants() {
        Tenant t = Tenant.builder().id("t").build();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(t));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t)));
        when(repository.search(eq("C"), eq(null), eq(null), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t, t)));
        when(repository.search(eq(null), eq("n"), eq(null), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t)));
        when(repository.search(eq(null), eq(null), eq(TenantType.PARTNER), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(repository.search(eq(null), eq(null), eq(null), eq(TenantStatus.ACTIVE), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t)));

        assertThat(service.list(new ListInput()).tenants()).containsExactly(t);
        assertThat(service.list(new ListPageInput(0, 10)).tenants().totalElements()).isEqualTo(1);
        assertThat(service.list(new ListPageInput(0, 10, "C", null, null, null)).tenants().totalElements()).isEqualTo(2);
        assertThat(service.list(new ListPageInput(0, 10, null, "n", null, null)).tenants().totalElements()).isEqualTo(1);
        assertThat(service.list(new ListPageInput(0, 10, null, null, TenantType.PARTNER, null)).tenants().empty()).isTrue();
        assertThat(service.list(new ListPageInput(0, 10, null, null, null, TenantStatus.ACTIVE)).tenants().totalElements()).isEqualTo(1);
    }

    @Test
    void deleteRemovesFromIdentityProviderThenRepository() {
        Tenant t = Tenant.builder().id("t").build();
        when(repository.findById("t")).thenReturn(Optional.of(t));
        assertThat(service.get(new GetInput("t")).tenant()).isSameAs(t);

        service.delete(new DeleteInput("t"));

        verify(idp).deleteOrganization(t);
        verify(repository).delete(t);
    }

    // ---- an organization's administrator sees only their own organization ------------------------------------------------

    private static void signedInAs(String tenantId, String role) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("u-1", null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
        com.heuristiq.uam.security.TenantContext.set(tenantId);
    }

    @org.junit.jupiter.api.AfterEach
    void clearCaller() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.heuristiq.uam.security.TenantContext.clear();
    }

    @Test
    void anOrganizationAdministratorListsOnlyTheirOwnOrganization() {
        Tenant herq = Tenant.builder().id("herq").code("HERQ").name("Heuristiq Payment Gateway").status(TenantStatus.ACTIVE).build();
        when(repository.findById("herq")).thenReturn(Optional.of(herq));
        signedInAs("herq", "ROLE_HERQ_ADMIN");

        var page = service.list(new ListPageInput(0, 10, null, null, null, null)).tenants();
        assertThat(page.content()).containsExactly(herq);
        assertThat(service.list(new ListInput()).tenants()).containsExactly(herq);

        assertThat(service.list(new ListPageInput(0, 10, "heur", "gateway", null, TenantStatus.ACTIVE)).tenants().content())
                .as("the same filters apply to their organization").containsExactly(herq);
        assertThat(service.list(new ListPageInput(0, 10, "ACME", null, null, null)).tenants().content()).isEmpty();
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void anOrganizationAdministratorCannotOpenAnotherOrganization() {
        when(repository.findById("acme")).thenReturn(Optional.of(Tenant.builder().id("acme").code("ACME").build()));
        signedInAs("herq", "ROLE_HERQ_ADMIN");

        assertThatThrownBy(() -> service.get(new GetInput("acme"))).isInstanceOf(UamException.class);
    }

    @Test
    void thePlatformAdministratorStillListsEveryOrganization() {
        Tenant herq = Tenant.builder().id("herq").code("HERQ").build();
        Tenant acme = Tenant.builder().id("acme").code("ACME").build();
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(acme, herq)));
        when(repository.findById("acme")).thenReturn(Optional.of(acme));
        signedInAs("default", com.heuristiq.uam.common.UamCommonConstants.SUPER_ADMIN_AUTHORITY);

        assertThat(service.list(new ListPageInput(0, 10, null, null, null, null)).tenants().content()).containsExactly(acme, herq);
        assertThat(service.get(new GetInput("acme")).tenant()).isSameAs(acme);
    }
}
