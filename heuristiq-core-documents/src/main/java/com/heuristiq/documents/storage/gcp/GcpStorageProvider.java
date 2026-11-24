package com.railcab.dms.storage.gcp;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.storage.StorageDigests;
import com.railcab.dms.storage.StorageProvider;
import com.railcab.dms.storage.encryption.EncryptionCapability;
import com.railcab.dms.storage.encryption.EncryptionMode;
import com.railcab.dms.storage.encryption.StorageEncryptionPolicy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class GcpStorageProvider implements StorageProvider {

    private final DmsProperties properties;
    private volatile Storage storage;

    public GcpStorageProvider(DmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(String provider) {
        return "gcp".equalsIgnoreCase(provider) || "gcp-storage".equalsIgnoreCase(provider) || "google-cloud-storage".equalsIgnoreCase(provider);
    }

    /** GCS always encrypts at rest; with mode SSE_KMS the object is written with the customer-managed key. */
    @Override
    public EncryptionCapability encryptionCapability() {
        return EncryptionCapability.PROVIDER_MANAGED_WITH_KMS;
    }

    /** Write options for the encryption settings: the customer-managed key (full resource name) when mode is SSE_KMS. */
    List<Storage.BlobWriteOption> writeOptions() {
        DmsProperties.Encryption encryption = properties.getStorage().getEncryption();
        if (encryption.getMode() == EncryptionMode.SSE_KMS) {
            return List.of(Storage.BlobWriteOption.kmsKeyName(encryption.getKmsKeyId()));
        }
        return List.of();
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file) {
        MessageDigest digest = StorageDigests.sha256();
        try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            BlobInfo info = BlobInfo.newBuilder(bucket(), objectKey).setContentType(file.getContentType()).build();
            Blob blob = writeOptions().isEmpty()
                    ? storage().createFrom(info, input)
                    : storage().createFrom(info, input, writeOptions().toArray(new Storage.BlobWriteOption[0]));
            String versionId = blob.getGeneration() == null ? null : String.valueOf(blob.getGeneration());
            return new StoredObject(objectKey, versionId, file.getSize(), file.getContentType(),
                    StorageDigests.hex(digest), StorageEncryptionPolicy.recordedEncryption(
                            EncryptionCapability.PROVIDER_MANAGED_WITH_KMS,
                            properties.getStorage().getEncryption().getMode()));
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to read document");
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to upload document to GCP Storage");
        }
    }

    @Override
    public Resource load(String objectKey) {
        Blob blob = storage().get(BlobId.of(bucket(), objectKey));
        if (blob == null || !blob.exists()) {
            throw DmsException.notFound("Document not found");
        }
        return new InputStreamResource(Channels.newInputStream(blob.reader()));
    }

    @Override
    public void delete(String objectKey) {
        try {
            storage().delete(BlobId.of(bucket(), objectKey));
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to delete document from GCP Storage");
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            return storage().get(bucket()) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Optional<PresignedDownload> presignDownload(String objectKey, Duration ttl) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket(), objectKey)).build();
            URL url = storage().signUrl(blobInfo, ttl.toMinutes(), TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
            return Optional.of(new PresignedDownload(url.toString(), Instant.now().plus(ttl)));
        } catch (Exception ex) {
            // Default application credentials often lack a private key to sign with; callers fall back gracefully.
            return Optional.empty();
        }
    }

    private String bucket() {
        return properties.getStorage().getGcp().getBucketName();
    }

    private Storage storage() {
        Storage existing = storage;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (storage == null) {
                storage = StorageOptions.getDefaultInstance().getService();
            }
            return storage;
        }
    }
}
