package com.heuristiq.identity.identity;

import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.dto.LoginResponse;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.ProviderTokenResponse;
import com.heuristiq.identity.auth.AuthServiceUseCase;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.identity.oidc.OidcIdentityProviderSyncService;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityProviderAdaptersTest {
    private final Tenant tenant = Tenant.builder().code("t").build();
    private final ApiClient client = ApiClient.builder().clientId("c").build();
    private final UamUser user = UamUser.builder().id("u1").userName("u").build();
    private final UamRole role = UamRole.builder().code("r").build();
    private final Permission permission = Permission.builder().code("p").build();

    private void exerciseEverything(IdentityProviderSyncAdapter adapter) {
        adapter.syncOrganization(tenant);
        adapter.syncClient(client);
        adapter.syncUser(user);
        adapter.setUserPassword(user, "pw", false);
        adapter.setTotpRequired(user, true);
        adapter.syncRole(role);
        adapter.syncPermission(permission);
        adapter.deleteOrganization(tenant);
        adapter.deleteClient(client);
        adapter.deleteUser(user);
        adapter.deleteRole(role);
        adapter.deletePermission(permission);
    }

    @Test
    void noneAdapterAcceptsEverythingAndDoesNothing() {
        NoopIdentityProviderSyncService adapter = new NoopIdentityProviderSyncService();
        assertThat(adapter.providerType()).isEqualTo(IdentityProviderType.NONE);
        assertThat(adapter.supports(IdentityProviderSyncOperation.SYNC_USER)).isTrue();
        assertThatCode(() -> exerciseEverything(adapter)).doesNotThrowAnyException();
    }

    @Test
    void oidcSyncAdapterDeclaresNothingSupportedAndIsHarmlessIfCalled() {
        OidcIdentityProviderSyncService adapter = new OidcIdentityProviderSyncService();
        assertThat(adapter.providerType()).isEqualTo(IdentityProviderType.OIDC);
        for (IdentityProviderSyncOperation operation : IdentityProviderSyncOperation.values()) {
            assertThat(adapter.supports(operation)).isFalse();
        }
        assertThatCode(() -> exerciseEverything(adapter)).doesNotThrowAnyException();
    }

    @Test
    void oktaPlaceholderRefusesEveryOperation() {
        UnsupportedIdentityProviderSyncService adapter = new UnsupportedIdentityProviderSyncService();
        assertThat(adapter.providerType()).isEqualTo(IdentityProviderType.OKTA);
        assertThat(adapter.supports(IdentityProviderSyncOperation.DELETE_USER)).isFalse();
        assertThatThrownBy(() -> adapter.syncOrganization(tenant)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Okta");
        assertThatThrownBy(() -> adapter.syncClient(client)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.syncUser(user)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.setUserPassword(user, "pw", true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.setTotpRequired(user, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.syncRole(role)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.syncPermission(permission)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.deleteOrganization(tenant)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.deleteClient(client)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.deleteUser(user)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.deleteRole(role)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.deletePermission(permission)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledAuthProviderAnswers503ForEveryCall() {
        NoopIdentityProviderAuthService none = new NoopIdentityProviderAuthService();
        assertThat(none.providerType()).isEqualTo(IdentityProviderType.NONE);
        ProviderRefreshRequest refresh = new ProviderRefreshRequest("rt");
        for (Runnable call : List.<Runnable>of(
                () -> none.login(new ProviderLoginRequest("u", "p", null)),
                () -> none.refresh(refresh),
                () -> none.logout(refresh),
                () -> none.fetchUserInfo("tok"))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getMessage()).contains("disabled");
            });
        }
    }

    @Test
    void externalIdentityResolverFindsOrProvisionsByEmail() {
        UamUserRepository repository = mock(UamUserRepository.class);
        ExternalIdentityResolver resolver = new ExternalIdentityResolver(repository);
        UamUser existing = UamUser.builder().id("e").email("a@x.com").status(UamEnums.UserStatus.ACTIVE).build();
        when(repository.findByEmailIgnoreCase("a@x.com")).thenReturn(Optional.of(existing));
        assertThat(resolver.resolveOrCreate(new ExternalUserInfo("s", "a@x.com", "A", "a"), null)).isSameAs(existing);

        when(repository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());
        when(repository.save(any(UamUser.class))).thenAnswer(inv -> {
            UamUser saved = inv.getArgument(0);
            saved.setStatus(UamEnums.UserStatus.ACTIVE);
            return saved;
        });
        UamUser created = resolver.resolveOrCreate(new ExternalUserInfo("s", "new@x.com", "New Person", "np"), null);
        assertThat(created.getEmail()).isEqualTo("new@x.com");
        assertThat(created.getUserName()).isEqualTo("new@x.com");
        assertThat(created.getRoles()).containsExactly("OPS");
        assertThat(created.getFullName().getFullName()).contains("New Person");

        UamUser typed = resolver.resolveOrCreate(new ExternalUserInfo("s", "new@x.com", null, "handle"), UamEnums.UserType.CUSTOMER);
        assertThat(typed.getRoles()).containsExactly("CUSTOMER");
        assertThat(typed.getFullName().getFullName()).contains("handle");

        UamUser anonymous = resolver.resolveOrCreate(new ExternalUserInfo("s", "new@x.com", " ", null), null);
        assertThat(anonymous.getFullName()).isNull();
    }

    @Test
    void externalIdentityNeverProvisionsAStaffAccountFromTheRequestedType() {
        UamUserRepository repository = mock(UamUserRepository.class);
        ExternalIdentityResolver resolver = new ExternalIdentityResolver(repository);
        when(repository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.empty());

        for (UamEnums.UserType staff : new UamEnums.UserType[] {
                UamEnums.UserType.SUPER_ADMIN, UamEnums.UserType.ADMIN, UamEnums.UserType.SECURITY}) {
            assertThatThrownBy(() -> resolver.resolveOrCreate(new ExternalUserInfo("s", "new@x.com", "N", "n"), staff))
                    .isInstanceOfSatisfying(com.heuristiq.identity.common.UamException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any(UamUser.class));

        // An existing account keeps signing in whatever type the request names: only creation is restricted.
        UamUser admin = UamUser.builder().id("a").email("admin@x.com").roles(java.util.List.of("ADMIN"))
                .status(UamEnums.UserStatus.ACTIVE).build();
        when(repository.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(admin));
        assertThat(resolver.resolveOrCreate(new ExternalUserInfo("s", "admin@x.com", "A", "a"), UamEnums.UserType.SUPER_ADMIN))
                .isSameAs(admin);
    }

    @Test
    void externalIdentityNeedsAnEmailAndAnActiveAccount() {
        UamUserRepository repository = mock(UamUserRepository.class);
        ExternalIdentityResolver resolver = new ExternalIdentityResolver(repository);
        assertThatThrownBy(() -> resolver.resolveOrCreate(new ExternalUserInfo("s", " ", "A", "a"), null))
                .isInstanceOf(UamException.class).hasMessageContaining("email claim");

        UamUser suspended = UamUser.builder().id("e").status(UamEnums.UserStatus.SUSPENDED).build();
        when(repository.findByEmailIgnoreCase("s@x.com")).thenReturn(Optional.of(suspended));
        assertThatThrownBy(() -> resolver.resolveOrCreate(new ExternalUserInfo("s", "s@x.com", "S", "s"), null))
                .isInstanceOf(UamException.class);
    }

    @Test
    void authRouterBridgesProviderLoginOntoALocalSession() {
        UamProperties properties = new UamProperties();
        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("keycloak");
        IdentityProviderAuthService keycloak = mock(IdentityProviderAuthService.class);
        when(keycloak.providerType()).thenReturn(IdentityProviderType.KEYCLOAK);
        IdentityProviderAuthService none = mock(IdentityProviderAuthService.class);
        when(none.providerType()).thenReturn(IdentityProviderType.NONE);
        ExternalIdentityResolver resolver = mock(ExternalIdentityResolver.class);
        AuthServiceUseCase auth = mock(AuthServiceUseCase.class);
        IdentityProviderAuthRouter router = new IdentityProviderAuthRouter(properties, List.of(keycloak, none), resolver, auth);

        ProviderTokenResponse token = new ProviderTokenResponse("access", "refresh", 60L, null, "Bearer", null);
        ExternalUserInfo info = new ExternalUserInfo("s", "a@x.com", "A", "a");
        LoginResponse session = mock(LoginResponse.class);
        when(keycloak.login(any())).thenReturn(token);
        when(keycloak.refresh(any())).thenReturn(token);
        when(keycloak.fetchUserInfo("access")).thenReturn(info);
        when(resolver.resolveOrCreate(any(), any())).thenReturn(user);
        when(auth.loginViaExternalProvider(any())).thenReturn(new AuthServiceUseCase.LoginViaExternalProviderOutput(session));

        // requested provider wins; blank falls back to configured one
        ProviderLoginRequest login = new ProviderLoginRequest("keycloak", "password", "u", "p", null, null, null, UamEnums.UserType.CUSTOMER);
        assertThat(router.login(new IdentityProviderAuthRouterUseCase.LoginInput(login)).response()).isSameAs(session);
        verify(resolver).resolveOrCreate(info, UamEnums.UserType.CUSTOMER);

        assertThat(router.refresh(new IdentityProviderAuthRouterUseCase.RefreshInput(new ProviderRefreshRequest(null, "rt"))).response())
                .isSameAs(session);
        ArgumentCaptor<ProviderRefreshRequest> captor = ArgumentCaptor.forClass(ProviderRefreshRequest.class);
        verify(keycloak).refresh(captor.capture());
        assertThat(captor.getValue().refreshToken()).isEqualTo("rt");

        router.logout(new IdentityProviderAuthRouterUseCase.LogoutInput(new ProviderRefreshRequest(" ", "rt")));
        verify(keycloak).logout(any());
    }

    @Test
    void authRouterUsesNoneWhenDisabledAndRejectsUnknownProviders() {
        UamProperties properties = new UamProperties();
        IdentityProviderAuthService none = mock(IdentityProviderAuthService.class);
        when(none.providerType()).thenReturn(IdentityProviderType.NONE);
        when(none.login(any())).thenThrow(new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, "disabled"));
        IdentityProviderAuthRouter router = new IdentityProviderAuthRouter(properties, List.of(none),
                mock(ExternalIdentityResolver.class), mock(AuthServiceUseCase.class));

        ProviderLoginRequest request = new ProviderLoginRequest("keycloak", "password", "u", "p", null, null, null, null);
        assertThatThrownBy(() -> router.login(new IdentityProviderAuthRouterUseCase.LoginInput(request)))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("oidc");
        assertThatThrownBy(() -> router.login(new IdentityProviderAuthRouterUseCase.LoginInput(
                new ProviderLoginRequest(null, "password", "u", "p", null, null, null, null))))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("oidc");
                });
    }
}
