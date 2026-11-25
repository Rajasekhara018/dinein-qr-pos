package com.heuristiq.identity.identity;

import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.organization.Tenant;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Primary
@Service
public class IdentityProviderSyncRouter implements IdentityProviderSyncService {
    private final UamProperties properties;
    private final Map<IdentityProviderType, IdentityProviderSyncAdapter> adapters;

    public IdentityProviderSyncRouter(UamProperties properties, List<IdentityProviderSyncAdapter> adapters) {
        this.properties = properties;
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IdentityProviderSyncAdapter::providerType,
                        Function.identity()));
    }

    public void syncOrganization(Tenant tenant) {
        execute(IdentityProviderSyncOperation.SYNC_ORGANIZATION, adapter -> adapter.syncOrganization(tenant));
    }

    public void syncClient(ApiClient client) {
        execute(IdentityProviderSyncOperation.SYNC_CLIENT, adapter -> adapter.syncClient(client));
    }

    @Override
    public void syncClient(ApiClient client, String rawSecret) {
        execute(IdentityProviderSyncOperation.SYNC_CLIENT, adapter -> adapter.syncClient(client, rawSecret));
    }

    public void syncUser(UamUser user) {
        execute(IdentityProviderSyncOperation.SYNC_USER, adapter -> adapter.syncUser(user));
    }

    public void setUserPassword(UamUser user, String rawPassword, boolean temporary) {
        execute(IdentityProviderSyncOperation.SET_USER_PASSWORD,
                adapter -> adapter.setUserPassword(user, rawPassword, temporary));
    }

    public void setTotpRequired(UamUser user, boolean required) {
        execute(IdentityProviderSyncOperation.SET_TOTP_REQUIRED,
                adapter -> adapter.setTotpRequired(user, required));
    }

    public void syncRole(UamRole role) {
        execute(IdentityProviderSyncOperation.SYNC_ROLE, adapter -> adapter.syncRole(role));
    }

    public void syncPermission(Permission permission) {
        execute(IdentityProviderSyncOperation.SYNC_PERMISSION, adapter -> adapter.syncPermission(permission));
    }

    public void deleteOrganization(Tenant tenant) {
        execute(IdentityProviderSyncOperation.DELETE_ORGANIZATION,
                adapter -> adapter.deleteOrganization(tenant));
    }

    public void deleteClient(ApiClient client) {
        execute(IdentityProviderSyncOperation.DELETE_CLIENT, adapter -> adapter.deleteClient(client));
    }

    public void deleteUser(UamUser user) {
        execute(IdentityProviderSyncOperation.DELETE_USER, adapter -> adapter.deleteUser(user));
    }

    public void deleteRole(UamRole role) {
        execute(IdentityProviderSyncOperation.DELETE_ROLE, adapter -> adapter.deleteRole(role));
    }

    public void deletePermission(Permission permission) {
        execute(IdentityProviderSyncOperation.DELETE_PERMISSION, adapter -> adapter.deletePermission(permission));
    }

    private void execute(IdentityProviderSyncOperation operation, Consumer<IdentityProviderSyncAdapter> action) {
        IdentityProviderSyncAdapter adapter = resolve();
        if (!adapter.supports(operation)) {
            handleUnsupported(adapter, operation);
            return;
        }
        action.accept(adapter);
    }

    private IdentityProviderSyncAdapter resolve() {
        String providerValue = properties.getIdentityProvider().isEnabled()
                ? properties.getIdentityProvider().getType()
                : "none";
        IdentityProviderType providerType = IdentityProviderType.from(providerValue);
        IdentityProviderSyncAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new ProviderAuthException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported identity provider sync adapter: " + providerValue);
        }
        return adapter;
    }

    private void handleUnsupported(IdentityProviderSyncAdapter adapter, IdentityProviderSyncOperation operation) {
        if ("skip".equalsIgnoreCase(properties.getIdentityProvider().getUnsupportedSyncAction())) {
            return;
        }
        throw new ProviderAuthException(
                HttpStatus.BAD_REQUEST,
                "Identity provider '" + adapter.providerType().value()
                        + "' does not support sync operation " + operation);
    }
}
