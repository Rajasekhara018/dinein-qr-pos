package com.heuristiq.uam.identity.keycloak;

import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.identity.IdentityProviderSyncAdapter;
import com.heuristiq.uam.identity.IdentityProviderType;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class KeycloakIdentityProviderSyncService implements IdentityProviderSyncAdapter {

    private final UamProperties properties;
    private final TenantRepository tenantRepository;
    private Keycloak keycloak;

    public KeycloakIdentityProviderSyncService(UamProperties properties, TenantRepository tenantRepository) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
    }

    public IdentityProviderType providerType() {
        return IdentityProviderType.KEYCLOAK;
    }

    @Override
    public void syncOrganization(Tenant tenant) {
        String realmName = realmName(tenant);
        RealmRepresentation representation = new RealmRepresentation();
        representation.setRealm(realmName);
        representation.setDisplayName(tenant.getName());
        representation.setEnabled(Boolean.TRUE.equals(tenant.getActive()));

        if (realmExists(realmName)) {
            keycloak().realm(realmName).update(representation);
        } else {
            keycloak().realms().create(representation);
        }
    }

    @Override
    public void syncClient(ApiClient client) {
        syncClient(client, null);
    }

    /** As {@link #syncClient(ApiClient)}; a non-blank {@code rawSecret} also becomes the confidential client's secret in Keycloak. */
    @Override
    public void syncClient(ApiClient client, String rawSecret) {
        RealmResource realm = realm(resolveRealmName(client));
        String clientUuid = findClientUuid(realm, client.getClientId());
        boolean publicClient = !StringUtils.hasText(client.getClientSecretHash());

        ClientRepresentation representation = new ClientRepresentation();
        representation.setClientId(client.getClientId());
        representation.setName(client.getName());
        representation.setDescription(client.getDescription());
        representation.setEnabled(Boolean.TRUE.equals(client.getActive()));
        representation.setPublicClient(publicClient);
        representation.setRedirectUris(setToList(client.getRedirectUris()));
        representation.setServiceAccountsEnabled(grantTypeEnabled(client, UamEnums.GrantType.CLIENT_CREDENTIALS));
        representation.setDirectAccessGrantsEnabled(client.getGrantTypes() == null
                || grantTypeEnabled(client, UamEnums.GrantType.PASSWORD));
        representation.setStandardFlowEnabled(client.getGrantTypes() == null
                || grantTypeEnabled(client, UamEnums.GrantType.AUTHORIZATION_CODE));
        // Without this Keycloak generates its own secret and the one UAM showed the user is refused at the token endpoint.
        // Left unset (null) on a plain update, Keycloak keeps the secret it has.
        if (!publicClient && StringUtils.hasText(rawSecret)) {
            representation.setClientAuthenticatorType(KeycloakConstants.CLIENT_SECRET_AUTHENTICATOR);
            representation.setSecret(rawSecret);
        }

        if (!StringUtils.hasText(clientUuid)) {
            try (Response response = realm.clients().create(representation)) {
                if (response.getStatus() >= 300) {
                    throw new IllegalStateException("Keycloak client create failed with status " + response.getStatus());
                }
            }
        } else {
            realm.clients().get(clientUuid).update(representation);
        }
    }

    @Override
    public void syncUser(UamUser user) {
        RealmResource realm = realm();
        String userId = findUserId(realm, user.getUserName());

        UserRepresentation representation = new UserRepresentation();
        representation.setUsername(user.getUserName());
        representation.setEmail(user.getEmail());
        representation.setFirstName(fullNameOf(user));
        representation.setEnabled(user.getStatus() == UamEnums.UserStatus.ACTIVE);
        representation.setEmailVerified(StringUtils.hasText(user.getEmail()));
        representation.singleAttribute(KeycloakConstants.USER_ID_ATTRIBUTE, user.getId());
        representation.singleAttribute(KeycloakConstants.UPDATED_AT_ATTRIBUTE, LocalDateTime.now().toString());

        if (!StringUtils.hasText(userId)) {
            try (Response response = realm.users().create(representation)) {
                if (response.getStatus() >= 300) {
                    throw new IllegalStateException("Keycloak user create failed with status " + response.getStatus());
                }
                userId = extractCreatedId(response);
            }
        } else {
            realm.users().get(userId).update(representation);
        }

        syncUserRoles(realm, userId, user.getRoles());
    }

    @Override
    public void setUserPassword(UamUser user, String rawPassword, boolean temporary) {
        RealmResource realm = realm();
        String userId = findUserId(realm, user.getUserName());
        if (!StringUtils.hasText(userId)) {
            syncUser(user);
            userId = findUserId(realm, user.getUserName());
        }
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(rawPassword);
        credential.setTemporary(temporary);
        realm.users().get(userId).resetPassword(credential);
    }

    @Override
    public void setTotpRequired(UamUser user, boolean required) {
        RealmResource realm = realm();
        String userId = findUserId(realm, user.getUserName());
        if (!StringUtils.hasText(userId)) {
            syncUser(user);
            userId = findUserId(realm, user.getUserName());
        }
        UserResource userResource = realm.users().get(userId);
        UserRepresentation representation = userResource.toRepresentation();
        List<String> actions = representation.getRequiredActions() == null
                ? new ArrayList<>()
                : new ArrayList<>(representation.getRequiredActions());
        if (required && !actions.contains(KeycloakConstants.CONFIGURE_TOTP_ACTION)) {
            actions.add(KeycloakConstants.CONFIGURE_TOTP_ACTION);
        }
        if (!required) {
            actions.remove(KeycloakConstants.CONFIGURE_TOTP_ACTION);
        }
        representation.setRequiredActions(actions);
        userResource.update(representation);
    }

    @Override
    public void syncRole(UamRole role) {
        RealmResource realm = realm();
        RoleRepresentation representation = new RoleRepresentation();
        representation.setName(role.getCode());
        representation.setDescription(role.getDescription());

        RoleResource roleResource = existingRole(realm, role.getCode());
        if (roleResource == null) {
            realm.roles().create(representation);
        } else {
            roleResource.update(representation);
        }
        assignPermissionComposites(realm, role.getCode(), role.getPermissions());
    }

    @Override
    public void syncPermission(Permission permission) {
        RealmResource realm = realm();
        String permissionRole = permissionRoleName(permission.getCode());
        RoleRepresentation representation = new RoleRepresentation();
        representation.setName(permissionRole);
        representation.setDescription(permissionDescription(permission));

        RoleResource roleResource = existingRole(realm, permissionRole);
        if (roleResource == null) {
            realm.roles().create(representation);
        } else {
            roleResource.update(representation);
        }
    }

    @Override
    public void deleteUser(UamUser user) {
        RealmResource realm = realm();
        String userId = findUserId(realm, user.getUserName());
        if (StringUtils.hasText(userId)) {
            realm.users().delete(userId);
        }
    }

    @Override
    public void deleteOrganization(Tenant tenant) {
        String realmName = realmName(tenant);
        if (realmExists(realmName)) {
            keycloak().realm(realmName).remove();
        }
    }

    @Override
    public void deleteClient(ApiClient client) {
        RealmResource realm = realm(resolveRealmName(client));
        String clientUuid = findClientUuid(realm, client.getClientId());
        if (StringUtils.hasText(clientUuid)) {
            realm.clients().get(clientUuid).remove();
        }
    }

    @Override
    public void deleteRole(UamRole role) {
        deleteRealmRole(role.getCode());
    }

    @Override
    public void deletePermission(Permission permission) {
        deleteRealmRole(permissionRoleName(permission.getCode()));
    }

    private Keycloak buildClient(UamProperties.Keycloak keycloakProperties) {
        if (StringUtils.hasText(keycloakProperties.getAdminUsername())
                && StringUtils.hasText(keycloakProperties.getAdminPassword())) {
            return KeycloakBuilder.builder()
                    .serverUrl(keycloakProperties.getServerUrl())
                    .realm(KeycloakConstants.ADMIN_REALM)
                    .clientId(KeycloakConstants.ADMIN_CLIENT_ID)
                    .username(keycloakProperties.getAdminUsername())
                    .password(keycloakProperties.getAdminPassword())
                    .grantType(OAuth2Constants.PASSWORD)
                    .build();
        }
        if (!StringUtils.hasText(keycloakProperties.getClientSecret())) {
            throw new IllegalStateException("Keycloak client secret or admin credentials must be configured");
        }
        return KeycloakBuilder.builder()
                .serverUrl(keycloakProperties.getServerUrl())
                .realm(keycloakProperties.getRealm())
                .clientId(keycloakProperties.getClientId())
                .clientSecret(keycloakProperties.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    private RealmResource realm() {
        return keycloak().realm(properties.getKeycloak().getRealm());
    }

    private RealmResource realm(String realmName) {
        return keycloak().realm(StringUtils.hasText(realmName) ? realmName : properties.getKeycloak().getRealm());
    }

    private boolean realmExists(String realmName) {
        try {
            keycloak().realm(realmName).toRepresentation();
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private Keycloak keycloak() {
        if (keycloak == null) {
            keycloak = buildClient(properties.getKeycloak());
        }
        return keycloak;
    }

    private boolean grantTypeEnabled(ApiClient client, UamEnums.GrantType type) {
        return client.getGrantTypes() != null && client.getGrantTypes().contains(type);
    }

    private String resolveRealmName(ApiClient client) {
        if (StringUtils.hasText(client.getTenantId())) {
            return tenantRepository.findById(client.getTenantId())
                    .map(this::realmName)
                    .orElse(properties.getKeycloak().getRealm());
        }
        return properties.getKeycloak().getRealm();
    }

    private String fullNameOf(UamUser user) {
        RName fullName = user.getFullName();
        return fullName == null ? null : fullName.getFullName();
    }

    private String findClientUuid(RealmResource realm, String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        return realm.clients().findByClientId(clientId).stream()
                .findFirst()
                .map(ClientRepresentation::getId)
                .orElse(null);
    }

    private String findUserId(RealmResource realm, String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return realm.users().searchByUsername(username, true).stream()
                .findFirst()
                .map(UserRepresentation::getId)
                .orElse(null);
    }

    private RoleResource existingRole(RealmResource realm, String code) {
        try {
            RoleResource resource = realm.roles().get(code);
            resource.toRepresentation();
            return resource;
        } catch (NotFoundException e) {
            return null;
        }
    }

    private void syncUserRoles(RealmResource realm, String userId, Collection<String> roles) {
        if (!StringUtils.hasText(userId) || roles == null || roles.isEmpty()) {
            return;
        }
        UserResource user = realm.users().get(userId);
        List<RoleRepresentation> roleRepresentations = roleRepresentations(realm, roles);
        if (!roleRepresentations.isEmpty()) {
            user.roles().realmLevel().add(roleRepresentations);
        }
    }

    private void assignPermissionComposites(RealmResource realm, String roleCode, Collection<String> permissionCodes) {
        if (!StringUtils.hasText(roleCode) || permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        RoleResource roleResource = existingRole(realm, roleCode);
        if (roleResource == null) {
            return;
        }
        List<RoleRepresentation> permissions = permissionCodes.stream()
                .filter(StringUtils::hasText)
                .map(this::permissionRoleName)
                .map(permissionRole -> existingRole(realm, permissionRole))
                .filter(resource -> resource != null)
                .map(RoleResource::toRepresentation)
                .toList();
        if (!permissions.isEmpty()) {
            roleResource.addComposites(permissions);
        }
    }

    private List<RoleRepresentation> roleRepresentations(RealmResource realm, Collection<String> roles) {
        List<RoleRepresentation> representations = new ArrayList<>();
        for (String role : roles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            RoleResource resource = existingRole(realm, role);
            if (resource == null) {
                log.warn("Skipping Keycloak role assignment; role {} does not exist", role);
                continue;
            }
            representations.add(resource.toRepresentation());
        }
        return representations;
    }

    private String extractCreatedId(Response response) {
        URI location = response.getLocation();
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private void deleteRealmRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return;
        }
        RoleResource roleResource = existingRole(realm(), roleName);
        if (roleResource != null) {
            roleResource.remove();
        }
    }

    private String permissionRoleName(String permissionCode) {
        return KeycloakConstants.PERMISSION_PREFIX + permissionCode.toUpperCase();
    }

    private String permissionDescription(Permission permission) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.hasText(permission.getDisplayName())
                ? permission.getDisplayName()
                : StringUtils.hasText(permission.getDescription()) ? permission.getDescription() : permission.getCode());
        if (permission.getModule() != null) {
            builder.append(" | module=").append(permission.getModule());
        }
        if (permission.getResource() != null) {
            builder.append(" | resource=").append(permission.getResource());
        }
        if (permission.getAction() != null) {
            builder.append(" | action=").append(permission.getAction());
        }
        return builder.toString();
    }

    private String realmName(Tenant tenant) {
        return KeycloakConstants.TENANT_REALM_PREFIX + tenant.getCode().toLowerCase();
    }

    private List<String> setToList(Collection<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
