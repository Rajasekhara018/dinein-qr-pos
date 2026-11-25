package com.heuristiq.identity.identity.oidc;

import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.identity.IdentityProviderSyncAdapter;
import com.heuristiq.identity.identity.IdentityProviderSyncOperation;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.identity.IdentityProviderType;
import org.springframework.stereotype.Service;

@Service
public class OidcIdentityProviderSyncService implements IdentityProviderSyncAdapter {
    public IdentityProviderType providerType() {
        return IdentityProviderType.OIDC;
    }

    public boolean supports(IdentityProviderSyncOperation operation) {
        return false;
    }

    public void syncOrganization(Tenant tenant) {
    }

    public void syncClient(ApiClient client) {
    }

    public void syncUser(UamUser user) {
    }

    public void setUserPassword(UamUser user, String rawPassword, boolean temporary) {
    }

    public void setTotpRequired(UamUser user, boolean required) {
    }

    public void syncRole(UamRole role) {
    }

    public void syncPermission(Permission permission) {
    }

    public void deleteOrganization(Tenant tenant) {
    }

    public void deleteClient(ApiClient client) {
    }

    public void deleteUser(UamUser user) {
    }

    public void deleteRole(UamRole role) {
    }

    public void deletePermission(Permission permission) {
    }

}
