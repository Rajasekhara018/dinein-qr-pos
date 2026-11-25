package com.heuristiq.identity.identity;

import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.organization.Tenant;
import org.springframework.stereotype.Service;

@Service
public class UnsupportedIdentityProviderSyncService implements IdentityProviderSyncAdapter {
    public IdentityProviderType providerType() {
        return IdentityProviderType.OKTA;
    }

    public boolean supports(IdentityProviderSyncOperation operation) {
        return false;
    }

    public void syncOrganization(Tenant tenant) {
        throw unsupported();
    }

    public void syncClient(ApiClient client) {
        throw unsupported();
    }

    public void syncUser(UamUser user) {
        throw unsupported();
    }

    public void setUserPassword(UamUser user, String rawPassword, boolean temporary) {
        throw unsupported();
    }

    public void setTotpRequired(UamUser user, boolean required) {
        throw unsupported();
    }

    public void syncRole(UamRole role) {
        throw unsupported();
    }

    public void syncPermission(Permission permission) {
        throw unsupported();
    }

    public void deleteOrganization(Tenant tenant) {
        throw unsupported();
    }

    public void deleteClient(ApiClient client) {
        throw unsupported();
    }

    public void deleteUser(UamUser user) {
        throw unsupported();
    }

    public void deleteRole(UamRole role) {
        throw unsupported();
    }

    public void deletePermission(Permission permission) {
        throw unsupported();
    }

    private IllegalStateException unsupported() {
        return new IllegalStateException("Okta identity provider is configured but no Okta sync adapter is installed.");
    }
}
