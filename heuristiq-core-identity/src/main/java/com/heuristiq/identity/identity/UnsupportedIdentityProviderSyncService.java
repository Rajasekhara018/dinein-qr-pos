package com.heuristiq.uam.identity;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.organization.Tenant;
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
