package com.heuristiq.documents.storage.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.storage.StorageDigests;
import com.heuristiq.documents.storage.StorageProvider;
import com.heuristiq.documents.storage.encryption.EncryptionCapability;
import com.heuristiq.documents.storage.encryption.StorageEncryptionConstants;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Component
public class AzureBlobStorageProvider implements StorageProvider {

    private final DmsProperties properties;
    private volatile BlobContainerClient container;

    public AzureBlobStorageProvider(DmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(String provider) {
        return "azure".equalsIgnoreCase(provider) || "azure-blob".equalsIgnoreCase(provider);
    }

    /** Azure Storage always encrypts at rest with Microsoft-managed keys; a per-request key is not supported here. */
    @Override
    public EncryptionCapability encryptionCapability() {
        return EncryptionCapability.PROVIDER_MANAGED;
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file) {
        MessageDigest digest = StorageDigests.sha256();
        try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            BlobClient blob = container().getBlobClient(objectKey);
            blob.upload(input, file.getSize(), true);
            blob.setHttpHeaders(new BlobHttpHeaders().setContentType(file.getContentType()));
            return new StoredObject(objectKey, null, file.getSize(), file.getContentType(), StorageDigests.hex(digest),
                    StorageEncryptionConstants.PROVIDER_MANAGED);
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to read document");
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to upload document to Azure Blob");
        }
    }

    @Override
    public Resource load(String objectKey) {
        try {
            return new InputStreamResource(container().getBlobClient(objectKey).openInputStream());
        } catch (Exception ex) {
            throw DmsException.notFound("Document not found");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            container().getBlobClient(objectKey).deleteIfExists();
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to delete document from Azure Blob");
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            container().exists();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Optional<PresignedDownload> presignDownload(String objectKey, Duration ttl) {
        try {
            BlobClient blob = container().getBlobClient(objectKey);
            OffsetDateTime expiry = OffsetDateTime.now().plus(ttl);
            BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
            String sas = blob.generateSas(new BlobServiceSasSignatureValues(expiry, permission));
            return Optional.of(new PresignedDownload(blob.getBlobUrl() + "?" + sas, expiry.toInstant()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private BlobContainerClient container() {
        BlobContainerClient existing = container;
        if (existing != null) {
            return existing;
        }
        DmsProperties.AzureBlob azure = properties.getStorage().getAzureBlob();
        if (!StringUtils.hasText(azure.getConnectionString())) {
            throw DmsException.badRequest("Azure Blob connection string is not configured");
        }
        synchronized (this) {
            if (container == null) {
                container = new BlobContainerClientBuilder()
                        .connectionString(azure.getConnectionString())
                        .containerName(azure.getContainerName())
                        .buildClient();
            }
            return container;
        }
    }
}
