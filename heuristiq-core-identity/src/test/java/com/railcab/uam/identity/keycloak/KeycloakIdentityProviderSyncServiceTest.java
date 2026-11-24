package com.heuristiq.uam.identity.keycloak;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.identity.IdentityProviderType;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.users.UamUser;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RealmsResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakIdentityProviderSyncServiceTest {
    private UamProperties properties;
    private TenantRepository tenantRepository;
    private Keycloak keycloak;
    private RealmsResource realms;
    private RealmResource realm;
    private UsersResource users;
    private RolesResource roles;
    private ClientsResource clients;
    private KeycloakIdentityProviderSyncService service;

    @BeforeEach
    void setUp() {
        properties = new UamProperties();
        properties.getKeycloak().setRealm("railcab");
        tenantRepository = mock(TenantRepository.class);
        keycloak = mock(Keycloak.class);
        realms = mock(RealmsResource.class);
        realm = mock(RealmResource.class);
        users = mock(UsersResource.class);
        roles = mock(RolesResource.class);
        clients = mock(ClientsResource.class);
        when(keycloak.realm(anyString())).thenReturn(realm);
        when(keycloak.realms()).thenReturn(realms);
        when(realm.users()).thenReturn(users);
        when(realm.roles()).thenReturn(roles);
        when(realm.clients()).thenReturn(clients);
        service = new KeycloakIdentityProviderSyncService(properties, tenantRepository);
        ReflectionTestUtils.setField(service, "keycloak", keycloak);
    }

    private static Response response(int status, String location) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.getLocation()).thenReturn(location == null ? null : URI.create(location));
        return response;
    }

    private static Tenant tenant(boolean active) {
        return Tenant.builder().code("ACME").name("Acme").active(active).build();
    }

    private RoleResource existingRole(String name) {
        RoleResource resource = mock(RoleResource.class);
        RoleRepresentation rep = new RoleRepresentation();
        rep.setName(name);
        when(resource.toRepresentation()).thenReturn(rep);
        when(roles.get(name)).thenReturn(resource);
        return resource;
    }

    private void missingRole(String name) {
        RoleResource resource = mock(RoleResource.class);
        when(resource.toRepresentation()).thenThrow(new NotFoundException());
        when(roles.get(name)).thenReturn(resource);
    }

    private UserRepresentation userRep(String id) {
        UserRepresentation rep = new UserRepresentation();
        rep.setId(id);
        return rep;
    }

    @Test
    void providerTypeIsKeycloak() {
        assertThat(service.providerType()).isEqualTo(IdentityProviderType.KEYCLOAK);
    }

    @Test
    void syncOrganizationCreatesRealmWhenMissing() {
        RealmResource acme = mock(RealmResource.class);
        when(keycloak.realm(KeycloakConstants.TENANT_REALM_PREFIX + "acme")).thenReturn(acme);
        when(acme.toRepresentation()).thenThrow(new NotFoundException());

        service.syncOrganization(tenant(true));

        ArgumentCaptor<RealmRepresentation> captor = ArgumentCaptor.forClass(RealmRepresentation.class);
        verify(realms).create(captor.capture());
        assertThat(captor.getValue().getRealm()).isEqualTo(KeycloakConstants.TENANT_REALM_PREFIX + "acme");
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Acme");
    }

    @Test
    void syncOrganizationUpdatesExistingRealmAndDisablesInactive() {
        RealmResource acme = mock(RealmResource.class);
        when(keycloak.realm(KeycloakConstants.TENANT_REALM_PREFIX + "acme")).thenReturn(acme);

        service.syncOrganization(tenant(false));

        ArgumentCaptor<RealmRepresentation> captor = ArgumentCaptor.forClass(RealmRepresentation.class);
        verify(acme).update(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        verify(realms, never()).create(any());
    }

    @Test
    void deleteOrganizationRemovesRealmOnlyWhenPresent() {
        RealmResource acme = mock(RealmResource.class);
        when(keycloak.realm(KeycloakConstants.TENANT_REALM_PREFIX + "acme")).thenReturn(acme);
        service.deleteOrganization(tenant(true));
        verify(acme).remove();

        RealmResource gone = mock(RealmResource.class);
        when(gone.toRepresentation()).thenThrow(new NotFoundException());
        when(keycloak.realm(KeycloakConstants.TENANT_REALM_PREFIX + "acme")).thenReturn(gone);
        service.deleteOrganization(tenant(true));
        verify(gone, never()).remove();
    }

    @Test
    void syncClientCreatesPublicClientWithDefaultFlows() {
        when(clients.findByClientId("web")).thenReturn(List.of());
        Response created = response(201, null);
        when(clients.create(any())).thenReturn(created);
        ApiClient client = ApiClient.builder().clientId("web").name("Web").redirectUris(Set.of("http://x")).build();

        service.syncClient(client);

        ArgumentCaptor<ClientRepresentation> captor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clients).create(captor.capture());
        ClientRepresentation rep = captor.getValue();
        assertThat(rep.isPublicClient()).isTrue();
        assertThat(rep.isServiceAccountsEnabled()).isFalse();
        assertThat(rep.isDirectAccessGrantsEnabled()).isTrue();
        assertThat(rep.isStandardFlowEnabled()).isTrue();
        assertThat(rep.getRedirectUris()).containsExactly("http://x");
        verify(created).close();
    }

    @Test
    void syncClientFailsWhenCreateReturnsErrorStatus() {
        when(clients.findByClientId("web")).thenReturn(List.of());
        Response resp1 = response(500, null);
        when(clients.create(any())).thenReturn(resp1);
        ApiClient client = ApiClient.builder().clientId("web").build();

        assertThatThrownBy(() -> service.syncClient(client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    @Test
    void syncClientUpdatesExistingConfidentialClientUsingTenantRealm() {
        Tenant t = tenant(true);
        when(tenantRepository.findById("t1")).thenReturn(Optional.of(t));
        RealmResource acme = mock(RealmResource.class);
        ClientsResource acmeClients = mock(ClientsResource.class);
        ClientResource clientResource = mock(ClientResource.class);
        when(keycloak.realm(KeycloakConstants.TENANT_REALM_PREFIX + "acme")).thenReturn(acme);
        when(acme.clients()).thenReturn(acmeClients);
        ClientRepresentation existing = new ClientRepresentation();
        existing.setId("uuid-1");
        when(acmeClients.findByClientId("svc")).thenReturn(List.of(existing));
        when(acmeClients.get("uuid-1")).thenReturn(clientResource);
        ApiClient client = ApiClient.builder().clientId("svc").tenantId("t1").clientSecretHash("hash")
                .grantTypes(Set.of(UamEnums.GrantType.CLIENT_CREDENTIALS)).active(false).build();

        service.syncClient(client);

        ArgumentCaptor<ClientRepresentation> captor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clientResource).update(captor.capture());
        ClientRepresentation rep = captor.getValue();
        assertThat(rep.isPublicClient()).isFalse();
        assertThat(rep.isServiceAccountsEnabled()).isTrue();
        assertThat(rep.isDirectAccessGrantsEnabled()).isFalse();
        assertThat(rep.isStandardFlowEnabled()).isFalse();
        assertThat(rep.isEnabled()).isFalse();
        assertThat(rep.getRedirectUris()).isEmpty();
        // A plain update sends no secret, so Keycloak keeps the one it has.
        assertThat(rep.getSecret()).isNull();
    }

    @Test
    void syncClientWithSecretGivesKeycloakTheSecretUamHandedOut() {
        when(clients.findByClientId("partner")).thenReturn(List.of());
        Response created = response(201, null);
        when(clients.create(any())).thenReturn(created);
        ApiClient client = ApiClient.builder().clientId("partner").clientSecretHash("hash")
                .grantTypes(Set.of(UamEnums.GrantType.CLIENT_CREDENTIALS)).active(true).build();

        service.syncClient(client, "plain-secret");

        ArgumentCaptor<ClientRepresentation> captor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clients).create(captor.capture());
        ClientRepresentation rep = captor.getValue();
        assertThat(rep.isPublicClient()).isFalse();
        assertThat(rep.getSecret()).isEqualTo("plain-secret");
        assertThat(rep.getClientAuthenticatorType()).isEqualTo(KeycloakConstants.CLIENT_SECRET_AUTHENTICATOR);
        assertThat(rep.isServiceAccountsEnabled()).isTrue();
    }

    @Test
    void syncClientNeverSetsASecretOnAPublicClient() {
        when(clients.findByClientId("spa")).thenReturn(List.of());
        Response created = response(201, null);
        when(clients.create(any())).thenReturn(created);

        service.syncClient(ApiClient.builder().clientId("spa").build(), "ignored");

        ArgumentCaptor<ClientRepresentation> captor = ArgumentCaptor.forClass(ClientRepresentation.class);
        verify(clients).create(captor.capture());
        assertThat(captor.getValue().getSecret()).isNull();
    }

    @Test
    void syncClientFallsBackToDefaultRealmWhenTenantUnknown() {
        when(tenantRepository.findById("ghost")).thenReturn(Optional.empty());
        when(clients.findByClientId("a")).thenReturn(List.of());
        Response resp2 = response(201, null);
        when(clients.create(any())).thenReturn(resp2);

        service.syncClient(ApiClient.builder().clientId("a").tenantId("ghost").build());

        verify(keycloak).realm("railcab");
    }

    @Test
    void syncClientWithBlankClientIdCreates() {
        Response resp3 = response(201, null);
        when(clients.create(any())).thenReturn(resp3);
        service.syncClient(ApiClient.builder().clientId(" ").build());
        verify(clients, never()).findByClientId(anyString());
        verify(clients).create(any());
    }

    @Test
    void deleteClientRemovesExistingOnly() {
        ClientRepresentation existing = new ClientRepresentation();
        existing.setId("u1");
        ClientResource resource = mock(ClientResource.class);
        when(clients.findByClientId("web")).thenReturn(List.of(existing));
        when(clients.get("u1")).thenReturn(resource);
        service.deleteClient(ApiClient.builder().clientId("web").build());
        verify(resource).remove();

        when(clients.findByClientId("web")).thenReturn(List.of());
        service.deleteClient(ApiClient.builder().clientId("web").build());
        verify(clients).get("u1");
    }

    @Test
    void syncUserCreatesUserAndAssignsExistingRolesSkippingMissing() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of());
        Response resp4 = response(201, "http://kc/admin/users/new-id");
        when(users.create(any())).thenReturn(resp4);
        UserResource userResource = mock(UserResource.class);
        RoleMappingResource mapping = mock(RoleMappingResource.class);
        RoleScopeResource scope = mock(RoleScopeResource.class);
        when(users.get("new-id")).thenReturn(userResource);
        when(userResource.roles()).thenReturn(mapping);
        when(mapping.realmLevel()).thenReturn(scope);
        existingRole("ADMIN");
        missingRole("GHOST");
        UamUser user = UamUser.builder().id("u1").userName("jane").email("j@x.com")
                .fullName(new RName("Jane", null, "Doe")).status(UamEnums.UserStatus.ACTIVE)
                .roles(List.of("ADMIN", "GHOST", " ")).build();

        service.syncUser(user);

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(users).create(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("jane");
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().isEmailVerified()).isTrue();
        assertThat(captor.getValue().getAttributes()).containsKey(KeycloakConstants.USER_ID_ATTRIBUTE);
        ArgumentCaptor<List<RoleRepresentation>> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(scope).add(roleCaptor.capture());
        assertThat(roleCaptor.getValue()).extracting(RoleRepresentation::getName).containsExactly("ADMIN");
    }

    @Test
    void syncUserUpdatesExistingUserAndSkipsRolesWhenNone() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(userRep("kc-1")));
        UserResource userResource = mock(UserResource.class);
        when(users.get("kc-1")).thenReturn(userResource);
        UamUser user = UamUser.builder().userName("jane").status(UamEnums.UserStatus.SUSPENDED).build();

        service.syncUser(user);

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().isEmailVerified()).isFalse();
        verify(userResource, never()).roles();
    }

    @Test
    void syncUserFailsWhenCreateRejected() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of());
        Response resp5 = response(409, null);
        when(users.create(any())).thenReturn(resp5);
        assertThatThrownBy(() -> service.syncUser(UamUser.builder().userName("jane").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("409");
    }

    @Test
    void syncUserToleratesMissingLocationHeaderAndPathWithoutSlash() {
        when(users.searchByUsername("a", true)).thenReturn(List.of());
        Response resp6 = response(201, null);
        when(users.create(any())).thenReturn(resp6);
        missingRole("R");
        service.syncUser(UamUser.builder().userName("a").roles(List.of("R")).build());
        verify(users, never()).get(anyString());

        when(users.searchByUsername("b", true)).thenReturn(List.of());
        Response resp7 = response(201, "plain");
        when(users.create(any())).thenReturn(resp7);
        UserResource ur = mock(UserResource.class);
        when(users.get("plain")).thenReturn(ur);
        missingRole("OTHER");
        service.syncUser(UamUser.builder().userName("b").roles(List.of("OTHER")).build());
        verify(users).get("plain");
    }

    @Test
    void setUserPasswordResetsForExistingUser() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(userRep("kc-1")));
        UserResource userResource = mock(UserResource.class);
        when(users.get("kc-1")).thenReturn(userResource);

        service.setUserPassword(UamUser.builder().userName("jane").build(), "Secret1!", true);

        ArgumentCaptor<CredentialRepresentation> captor = ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource).resetPassword(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("Secret1!");
        assertThat(captor.getValue().isTemporary()).isTrue();
        assertThat(captor.getValue().getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    }

    @Test
    void setUserPasswordSyncsMissingUserFirst() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(), List.of(), List.of(userRep("kc-2")));
        Response resp8 = response(201, "http://kc/users/kc-2");
        when(users.create(any())).thenReturn(resp8);
        UserResource userResource = mock(UserResource.class);
        when(users.get("kc-2")).thenReturn(userResource);

        service.setUserPassword(UamUser.builder().userName("jane").build(), "pw", false);

        verify(users).create(any());
        verify(userResource).resetPassword(any(CredentialRepresentation.class));
    }

    @Test
    void setTotpRequiredAddsAndRemovesAction() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(userRep("kc-1")));
        UserResource userResource = mock(UserResource.class);
        when(users.get("kc-1")).thenReturn(userResource);
        UserRepresentation rep = new UserRepresentation();
        when(userResource.toRepresentation()).thenReturn(rep);
        UamUser user = UamUser.builder().userName("jane").build();

        service.setTotpRequired(user, true);
        assertThat(rep.getRequiredActions()).containsExactly(KeycloakConstants.CONFIGURE_TOTP_ACTION);

        service.setTotpRequired(user, true);
        assertThat(rep.getRequiredActions()).containsExactly(KeycloakConstants.CONFIGURE_TOTP_ACTION);

        service.setTotpRequired(user, false);
        assertThat(rep.getRequiredActions()).isEmpty();
        verify(userResource, org.mockito.Mockito.times(3)).update(rep);
    }

    @Test
    void setTotpRequiredSyncsMissingUser() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(), List.of(), List.of(userRep("kc-9")));
        Response resp9 = response(201, "http://kc/users/kc-9");
        when(users.create(any())).thenReturn(resp9);
        UserResource userResource = mock(UserResource.class);
        when(users.get("kc-9")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

        service.setTotpRequired(UamUser.builder().userName("jane").build(), true);

        verify(userResource).update(any(UserRepresentation.class));
    }

    @Test
    void deleteUserDeletesOnlyWhenFound() {
        when(users.searchByUsername("jane", true)).thenReturn(List.of(userRep("kc-1")));
        service.deleteUser(UamUser.builder().userName("jane").build());
        verify(users).delete("kc-1");

        service.deleteUser(UamUser.builder().userName(null).build());
        verify(users, org.mockito.Mockito.times(1)).delete(anyString());
    }

    @Test
    void syncRoleCreatesAndAssignsPermissionComposites() {
        missingRole("MANAGER");
        // after creation the composite lookup finds the role
        RoleResource manager = mock(RoleResource.class);
        when(manager.toRepresentation()).thenThrow(new NotFoundException());
        when(roles.get("MANAGER")).thenReturn(manager);
        UamRole role = UamRole.builder().code("MANAGER").description("d").permissions(List.of("a")).build();

        service.syncRole(role);

        verify(roles).create(any(RoleRepresentation.class));
        verify(manager, never()).addComposites(anyList());
    }

    @Test
    void syncRoleUpdatesExistingAndAddsComposites() {
        RoleResource manager = existingRole("MANAGER");
        existingRole(KeycloakConstants.PERMISSION_PREFIX + "READ_X");
        missingRole(KeycloakConstants.PERMISSION_PREFIX + "MISSING");
        UamRole role = UamRole.builder().code("MANAGER").permissions(List.of("read_x", "missing", "")).build();

        service.syncRole(role);

        verify(manager).update(any(RoleRepresentation.class));
        ArgumentCaptor<List<RoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(manager).addComposites(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void syncRoleWithoutPermissionsDoesNotAddComposites() {
        RoleResource manager = existingRole("MANAGER");
        service.syncRole(UamRole.builder().code("MANAGER").build());
        verify(manager, never()).addComposites(anyList());
        missingRole(KeycloakConstants.PERMISSION_PREFIX + "ZZZ");
        service.syncRole(UamRole.builder().code("MANAGER").permissions(List.of("zzz")).build());
        verify(manager, never()).addComposites(anyList());
    }

    @Test
    void syncPermissionCreatesWithDescriptionVariants() {
        String name = KeycloakConstants.PERMISSION_PREFIX + "USER_READ";
        missingRole(name);

        service.syncPermission(Permission.builder().code("user_read").displayName("Read users")
                .module(UamEnums.ModuleCategory.values()[0]).build());

        ArgumentCaptor<RoleRepresentation> captor = ArgumentCaptor.forClass(RoleRepresentation.class);
        verify(roles).create(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
        assertThat(captor.getValue().getDescription()).startsWith("Read users | module=");
    }

    @Test
    void syncPermissionUpdatesExistingUsingDescriptionOrCodeFallback() {
        String name = KeycloakConstants.PERMISSION_PREFIX + "P1";
        RoleResource resource = existingRole(name);

        service.syncPermission(Permission.builder().code("p1").description("desc").build());
        service.syncPermission(Permission.builder().code("p1").build());

        ArgumentCaptor<RoleRepresentation> captor = ArgumentCaptor.forClass(RoleRepresentation.class);
        verify(resource, org.mockito.Mockito.times(2)).update(captor.capture());
        assertThat(captor.getAllValues()).extracting(RoleRepresentation::getDescription)
                .containsExactly("desc", "p1");
    }

    @Test
    void deleteRoleAndPermissionRemoveWhenExisting() {
        RoleResource role = existingRole("MANAGER");
        service.deleteRole(UamRole.builder().code("MANAGER").build());
        verify(role).remove();

        RoleResource perm = existingRole(KeycloakConstants.PERMISSION_PREFIX + "PX");
        service.deletePermission(Permission.builder().code("px").build());
        verify(perm).remove();

        missingRole("NOPE");
        service.deleteRole(UamRole.builder().code("NOPE").build());
        service.deleteRole(UamRole.builder().code(" ").build());
        verify(roles, never()).get(" ");
    }

    @Test
    void lazyClientBuildRequiresSecretOrAdminCredentials() {
        KeycloakIdentityProviderSyncService fresh = new KeycloakIdentityProviderSyncService(properties, tenantRepository);
        properties.getKeycloak().setClientSecret("");
        properties.getKeycloak().setAdminUsername("");

        assertThatThrownBy(() -> fresh.deleteUser(UamUser.builder().userName("x").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    void lazyClientBuildUsesClientSecretOrAdminPassword() {
        properties.getKeycloak().setClientSecret("s3cret");
        KeycloakIdentityProviderSyncService viaSecret = new KeycloakIdentityProviderSyncService(properties, tenantRepository);
        // building the admin client does not contact the server until a call is made
        assertThat((Object) ReflectionTestUtils.invokeMethod(viaSecret, "buildClient", properties.getKeycloak()))
                .isInstanceOf(Keycloak.class);

        properties.getKeycloak().setAdminUsername("admin");
        properties.getKeycloak().setAdminPassword("pw");
        assertThat((Object) ReflectionTestUtils.invokeMethod(viaSecret, "buildClient", properties.getKeycloak()))
                .isInstanceOf(Keycloak.class);
    }
}
