package com.heuristiq.uam.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.metrics.UamMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Giving an account more roles keeps the ones it has, takes nothing away, and respects who may hand out which role. */
class UamUserAddRolesTest {
    private UamUserRepository repository;
    private IdentityProviderSyncService idp;
    private UamUserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UamUserRepository.class);
        idp = mock(IdentityProviderSyncService.class);
        UamRoleRepository roles = mock(UamRoleRepository.class);
        when(repository.save(any(UamUser.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UamUserService(repository, mock(UserCredentialServiceUseCase.class), idp, roles, new UamMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private UamUser existing(List<String> roles) {
        UamUser user = UamUser.builder().id("u1").userName("sita").roles(roles).build();
        when(repository.findById("u1")).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void newRolesAreAddedAfterTheOnesTheAccountHasAndTheIdentityProviderIsTold() {
        existing(List.of("FRANCHISE_AGENT"));

        UamUser saved = service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of(" matri_viewer ", "FRANCHISE_AGENT", " "))).user();

        assertThat(saved.getRoles()).containsExactly("FRANCHISE_AGENT", "MATRI_VIEWER");
        verify(idp).syncUser(saved);
    }

    @Test
    void anAccountWithNoRolesYetGetsThem() {
        UamUser user = existing(null);

        assertThat(service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("A_ROLE"))).user().getRoles()).containsExactly("A_ROLE");
        assertThat(user.getRoles()).containsExactly("A_ROLE");
    }

    @Test
    void roleTheAccountAlreadyHasChangesNothingAndSavesNothing() {
        existing(List.of("A_ROLE"));

        service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("a_role")));

        verify(repository, never()).save(any(UamUser.class));
        verify(idp, never()).syncUser(any());
    }

    @Test
    void anUnknownAccountIsNotFound() {
        when(repository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addRoles(new UamUserServiceUseCase.AddRolesInput("nobody", List.of("A_ROLE")))).isInstanceOf(UamException.class);
    }

    @Test
    void onlyASuperAdminMayGiveTheSuperAdminRole() {
        existing(List.of("FRANCHISE_AGENT"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ops", null, List.of(new SimpleGrantedAuthority("ROLE_OPS"))));

        assertThatThrownBy(() -> service.addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("SUPER_ADMIN"))))
                .isInstanceOf(UamException.class).hasMessageContaining("super admin");
        verify(repository, never()).save(any(UamUser.class));
    }
}
