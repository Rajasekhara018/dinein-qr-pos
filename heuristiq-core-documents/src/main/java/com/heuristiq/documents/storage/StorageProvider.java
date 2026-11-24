package com.railcab.dms.storage;

import com.railcab.dms.storage.encryption.EncryptionCapability;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Optional;

public interface StorageProvider {

    boolean supports(String provider);

    StoredObject store(String objectKey, MultipartFile file);

    Resource load(String objectKey);

    default void delete(String objectKey) {
        throw new UnsupportedOperationException("Delete is not supported by this storage provider");
    }

    /**
     * What this provider can do about encryption at rest. Defaults to plaintext so a provider must opt in
     * explicitly; the startup guard uses it.
     */
    default EncryptionCapability encryptionCapability() {
        return EncryptionCapability.PLAINTEXT_ONLY;
    }

    default boolean isHealthy() {
        return true;
    }

    /**
     * Backends that can hand clients a direct, time-limited URL (S3/GCS presigned URL, Azure SAS)
     * should override this so downloads bypass the app tier. Default: unsupported.
     */
    default Optional<PresignedDownload> presignDownload(String objectKey, Duration ttl) {
        return Optional.empty();
    }

    /** @param encryption what the provider did for encryption at rest (NONE, SSE_S3, SSE_KMS or PROVIDER_MANAGED) */
    record StoredObject(String objectKey, String versionId, long sizeBytes, String contentType, String checksumSha256,
                        String encryption) {
    }

    record PresignedDownload(String url, java.time.Instant expiresAt) {
    }
}
