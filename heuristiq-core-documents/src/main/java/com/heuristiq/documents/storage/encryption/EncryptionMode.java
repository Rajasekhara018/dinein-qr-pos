package com.railcab.dms.storage.encryption;

/**
 * Server-side encryption at rest requested for stored documents ({@code railcab.dms.storage.encryption.mode}).
 */
public enum EncryptionMode {
    /** No encryption is requested from the storage backend. */
    NONE,
    /** Backend-managed keys (S3 {@code AES256}; MinIO SSE-S3). */
    SSE_S3,
    /** A customer-managed key identified by {@code kms-key-id}. */
    SSE_KMS
}
