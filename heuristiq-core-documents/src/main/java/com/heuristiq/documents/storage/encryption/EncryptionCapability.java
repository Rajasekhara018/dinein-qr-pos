package com.heuristiq.documents.storage.encryption;

/**
 * What a storage provider can do about encryption at rest. Each {@code StorageProvider} declares one.
 */
public enum EncryptionCapability {
    /** Stores plaintext (local disk); no encryption mode other than NONE is possible. */
    PLAINTEXT_ONLY,
    /** Honours NONE, SSE_S3 and SSE_KMS on the write request (S3, MinIO). */
    SERVER_SIDE,
    /** Always encrypts with provider-managed keys; honours SSE_KMS as a customer-managed key (GCP). */
    PROVIDER_MANAGED_WITH_KMS,
    /** Always encrypts with provider-managed keys; ignores the mode and cannot take a key per request (Azure). */
    PROVIDER_MANAGED
}
