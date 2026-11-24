package com.heuristiq.uam.identity;

public interface IdentityProviderSyncAdapter extends IdentityProviderSyncService {
    IdentityProviderType providerType();

    default boolean supports(IdentityProviderSyncOperation operation) {
        return true;
    }
}
