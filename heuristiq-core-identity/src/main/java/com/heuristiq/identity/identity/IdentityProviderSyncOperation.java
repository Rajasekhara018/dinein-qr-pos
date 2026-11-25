package com.heuristiq.identity.identity;

public enum IdentityProviderSyncOperation {
    SYNC_ORGANIZATION,
    SYNC_CLIENT,
    SYNC_USER,
    SET_USER_PASSWORD,
    SET_TOTP_REQUIRED,
    SYNC_ROLE,
    SYNC_PERMISSION,
    DELETE_ORGANIZATION,
    DELETE_CLIENT,
    DELETE_USER,
    DELETE_ROLE,
    DELETE_PERMISSION
}
