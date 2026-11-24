package com.heuristiq.uam.identity;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.organization.Tenant;
import org.springframework.stereotype.Service;

@Service
public class NoopIdentityProviderSyncService implements IdentityProviderSyncAdapter {
    public IdentityProviderType providerType() {
        return IdentityProviderType.NONE;
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
