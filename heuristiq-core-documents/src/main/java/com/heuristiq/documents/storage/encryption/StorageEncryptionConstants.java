package com.heuristiq.documents.storage.encryption;

/** Fixed values of the encryption-at-rest feature. */
public final class StorageEncryptionConstants {

    private StorageEncryptionConstants() {
    }

    /** Recorded on a document when the provider encrypted it with its own keys without a per-request setting. */
    public static final String PROVIDER_MANAGED = "PROVIDER_MANAGED";
}
