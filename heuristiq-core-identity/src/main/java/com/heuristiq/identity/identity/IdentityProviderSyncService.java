package com.heuristiq.identity.identity;

import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.organization.Tenant;

public interface IdentityProviderSyncService {
    void syncOrganization(Tenant tenant);

    void syncClient(ApiClient client);

    /**
     * Creates or updates the client and sets its secret to {@code rawSecret}, so the secret UAM hands out (on create and on
     * rotate) is the one the identity provider accepts at its token endpoint. Providers that keep no client secret ignore it.
     */
    default void syncClient(ApiClient client, String rawSecret) {
        syncClient(client);
    }

    void syncUser(UamUser user);

    void setUserPassword(UamUser user, String rawPassword, boolean temporary);

    void setTotpRequired(UamUser user, boolean required);

    void syncRole(UamRole role);

    void syncPermission(Permission permission);

    void deleteOrganization(Tenant tenant);

    void deleteClient(ApiClient client);

    void deleteUser(UamUser user);

    void deleteRole(UamRole role);

    void deletePermission(Permission permission);

}
