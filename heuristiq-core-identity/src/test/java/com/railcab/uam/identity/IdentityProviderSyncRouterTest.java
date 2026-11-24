package com.heuristiq.uam.identity;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.users.UamUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityProviderSyncRouterTest {
    private UamProperties properties;
    private IdentityProviderSyncAdapter none;
    private IdentityProviderSyncAdapter keycloak;
    private IdentityProviderSyncRouter router;

    private static IdentityProviderSyncAdapter adapter(IdentityProviderType type) {
        IdentityProviderSyncAdapter a = mock(IdentityProviderSyncAdapter.class);
        when(a.providerType()).thenReturn(type);
        when(a.supports(any())).thenReturn(true);
        return a;
    }

    @BeforeEach
    void setUp() {
        properties = new UamProperties();
        none = adapter(IdentityProviderType.NONE);
        keycloak = adapter(IdentityProviderType.KEYCLOAK);
        router = new IdentityProviderSyncRouter(properties, List.of(none, keycloak));
    }

    @Test
    void disabledProviderRoutesToNoneAdapter() {
        UamUser user = UamUser.builder().userName("u").build();
        router.syncUser(user);
        verify(none).syncUser(user);
        verify(keycloak, never()).syncUser(any());
    }

    @Test
    void enabledProviderRoutesEveryOperationToItsAdapter() {
        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("keycloak");
        Tenant t = Tenant.builder().code("c").build();
        ApiClient c = ApiClient.builder().clientId("c").build();
        UamUser u = UamUser.builder().userName("u").build();
        UamRole r = UamRole.builder().code("r").build();
        Permission p = Permission.builder().code("p").build();

        router.syncOrganization(t);
        router.syncClient(c);
        router.syncClient(c, "secret");
        router.syncUser(u);
        router.setUserPassword(u, "pw", true);
        router.setTotpRequired(u, false);
        router.syncRole(r);
        router.syncPermission(p);
        router.deleteOrganization(t);
        router.deleteClient(c);
        router.deleteUser(u);
        router.deleteRole(r);
        router.deletePermission(p);

        verify(keycloak).syncOrganization(t);
        verify(keycloak).syncClient(c);
        verify(keycloak).syncClient(c, "secret");
        verify(keycloak).syncUser(u);
        verify(keycloak).setUserPassword(u, "pw", true);
        verify(keycloak).setTotpRequired(u, false);
        verify(keycloak).syncRole(r);
        verify(keycloak).syncPermission(p);
        verify(keycloak).deleteOrganization(t);
        verify(keycloak).deleteClient(c);
        verify(keycloak).deleteUser(u);
        verify(keycloak).deleteRole(r);
        verify(keycloak).deletePermission(p);
        verify(none, never()).syncUser(any());
    }

    @Test
    void missingAdapterIsRejected() {
        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("oidc");
        assertThatThrownBy(() -> router.syncUser(UamUser.builder().build()))
                .isInstanceOfSatisfying(ProviderAuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("oidc");
                });
    }

    @Test
    void unknownProviderTypeIsRejected() {
        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("saml");
        assertThatThrownBy(() -> router.syncUser(UamUser.builder().build()))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("saml");
    }

    @Test
    void unsupportedOperationFailsByDefaultAndIsSkippedWhenConfigured() {
        properties.getIdentityProvider().setEnabled(true);
        properties.getIdentityProvider().setType("keycloak");
        when(keycloak.supports(IdentityProviderSyncOperation.SYNC_USER)).thenReturn(false);
        UamUser u = UamUser.builder().build();

        assertThatThrownBy(() -> router.syncUser(u))
                .isInstanceOf(ProviderAuthException.class).hasMessageContaining("does not support");

        properties.getIdentityProvider().setUnsupportedSyncAction("SKIP");
        router.syncUser(u);
        verify(keycloak, never()).syncUser(u);
    }

    @Test
    void identityProviderTypeParsing() {
        assertThat(IdentityProviderType.from(null)).isEqualTo(IdentityProviderType.NONE);
        assertThat(IdentityProviderType.from("  ")).isEqualTo(IdentityProviderType.NONE);
        assertThat(IdentityProviderType.from(" KeyCloak ")).isEqualTo(IdentityProviderType.KEYCLOAK);
        assertThat(IdentityProviderType.OKTA.value()).isEqualTo("okta");
    }
}
