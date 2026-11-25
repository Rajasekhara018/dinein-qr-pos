package com.heuristiq.identity.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.metrics.UamMetrics;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.repository.UamUserRepository;
import com.heuristiq.identity.users.repository.UamUserSearchCriteria;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Who may hand out which role, and which organization's people a session sees: an organization admin cannot raise anyone (or
 * themselves) above their own access, and cannot widen the user list beyond their own organization.
 */
class UamUserRoleGrantRulesTest {
    private UamUserRepository users;
    private UamRoleRepository roles;
    private UamUserService service;

    @BeforeEach
    void setUp() {
        users = mock(UamUserRepository.class);
        roles = mock(UamRoleRepository.class);
        when(users.save(any(UamUser.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UamUserService(users, mock(UserCredentialServiceUseCase.class), mock(IdentityProviderSyncService.class), roles,
                new UamMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void signInAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private void role(String code, String tenant, String... permissions) {
        when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(UamRole.builder().code(code).tenantId(tenant)
                .permissions(List.of(permissions)).build()));
    }

    private UamUser account(List<String> held) {
        UamUser user = UamUser.builder().id("u1").userName("sita").roles(held).build();
        when(users.findById("u1")).thenReturn(Optional.of(user));
        return user;
    }

    private static UpsertUserRequest request(List<String> roleCodes, UamEnums.UserType type) {
        return new UpsertUserRequest("jane", "Secret#123", "Jane Doe", type, "jane@example.com", "9876543210", roleCodes, null);
    }

    // ---- RU-2 / RU-16: nobody gives what they do not hold -----------------------------------------

    @Test
    void anOrganizationAdminCannotGiveARoleThatCarriesMoreThanTheyHold() {
        signInAs("ROLE_ORG_ADMIN");
        role("ORG_ADMIN", "org-a", "USER_LIST");
        role("BROAD", null, "USER_LIST", "ROLE_DELETE");
        account(List.of());

        assertThatThrownBy(() -> service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("BROAD"))))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).contains("ROLE_DELETE");
                });
        verify(users, never()).save(any(UamUser.class));
    }

    @Test
    void anOrganizationAdminCanGiveARoleWithinTheirOwnAccess() {
        signInAs("ROLE_ORG_ADMIN");
        role("ORG_ADMIN", "org-a", "USER_LIST", "ROLE_LIST");
        role("VIEWER", null, "USER_LIST");
        account(List.of());

        assertThat(service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("VIEWER"))).user().getRoles()).containsExactly("VIEWER");
    }

    @Test
    void aRoleTheAccountAlreadyHasIsNotCheckedAgainWhenOtherRolesAreAdded() {
        signInAs("ROLE_ORG_ADMIN");
        role("ORG_ADMIN", "org-a", "USER_LIST");
        role("BROAD", null, "USER_LIST", "ROLE_DELETE");
        role("VIEWER", null, "USER_LIST");
        account(List.of("BROAD"));

        assertThat(service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("BROAD", "VIEWER"))).user().getRoles())
                .containsExactly("BROAD", "VIEWER");
    }

    @Test
    void anAdminCannotRaiseTheirOwnAccountThroughAnUpdate() {
        signInAs("ROLE_ORG_ADMIN");
        role("ORG_ADMIN", "org-a", "USER_LIST");
        role("BROAD", null, "USER_LIST", "ROLE_DELETE");
        account(List.of("ORG_ADMIN"));

        assertThatThrownBy(() -> service.update(new UamUserServiceUseCase.UpdateInput("u1", request(List.of("ORG_ADMIN", "BROAD"), null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(users, never()).save(any(UamUser.class));
    }

    @Test
    void theUserTypeShortcutIsHeldToTheSameRuleAsAnExplicitRole() {
        signInAs("ROLE_ORG_ADMIN");
        role("ORG_ADMIN", "org-a", "USER_LIST");
        role("ADMIN", null, "USER_LIST", "ROLE_DELETE");

        assertThatThrownBy(() -> service.create(new UamUserServiceUseCase.CreateInput(request(List.of(), UamEnums.UserType.ADMIN))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(users, never()).save(any(UamUser.class));
    }

    @Test
    void aSuperAdminAndWorkWithNoSessionMayGiveAnyRole() {
        role("BROAD", null, "USER_LIST", "ROLE_DELETE");
        account(List.of());
        service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("BROAD")));

        signInAs("ROLE_SUPER_ADMIN");
        UamUser other = account(List.of());
        assertThat(service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("BROAD"))).user()).isSameAs(other);
        assertThat(other.getRoles()).containsExactly("BROAD");
    }

    // ---- RU-3: the user list follows the session's organization ---------------------------------

    private UamUserSearchCriteria searched(Runnable action) {
        Page<UamUser> page = new PageImpl<>(List.of());
        when(users.search(any(UamUserSearchCriteria.class), any(Pageable.class))).thenReturn(page);
        action.run();
        ArgumentCaptor<UamUserSearchCriteria> criteria = ArgumentCaptor.forClass(UamUserSearchCriteria.class);
        verify(users).search(criteria.capture(), any(Pageable.class));
        return criteria.getValue();
    }

    @Test
    void anOrganizationSessionAlwaysSearchesItsOwnOrganizationWhateverTheRequestSays() {
        signInAs("ROLE_ORG_ADMIN");
        TenantContext.set("org-a");

        UamUserSearchCriteria criteria = searched(() ->
                service.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, "sita", "org-b")));

        assertThat(criteria.tenantScope()).isEqualTo("org-a");
    }

    @Test
    void anOrganizationSessionSearchWithNoScopeIsStillLimitedToItsOrganization() {
        signInAs("ROLE_ORG_ADMIN");
        TenantContext.set("org-a");

        assertThat(searched(() -> service.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, null, null))).tenantScope())
                .isEqualTo("org-a");
    }

    @Test
    void theUnfilteredListOfAnOrganizationSessionIsItsOwnPeopleNotEveryAccount() {
        signInAs("ROLE_ORG_ADMIN");
        TenantContext.set("org-a");

        UamUserSearchCriteria criteria = searched(() -> service.list(new UamUserServiceUseCase.ListPageInput(0, 10)));

        assertThat(criteria.tenantScope()).isEqualTo("org-a");
        verify(users, never()).findAll(any(Pageable.class));
    }

    @Test
    void aSuperAdminMayNameAnyOrganizationOrNone() {
        signInAs("ROLE_SUPER_ADMIN");
        TenantContext.set("org-a");

        assertThat(searched(() -> service.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, null, "org-b"))).tenantScope())
                .isEqualTo("org-b");
    }

    @Test
    void workWithNoSessionKeepsTheRequestedScope() {
        assertThat(searched(() -> service.search(new UamUserServiceUseCase.SearchInput(0, 10, null, null, null, "org-b"))).tenantScope())
                .isEqualTo("org-b");
    }
}
