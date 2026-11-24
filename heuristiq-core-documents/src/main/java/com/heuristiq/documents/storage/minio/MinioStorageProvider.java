package com.heuristiq.documents.storage.minio;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.storage.StorageDigests;
import com.heuristiq.documents.storage.StorageProvider;
import com.heuristiq.documents.storage.aws.S3ClientFactory;
import com.heuristiq.documents.storage.aws.S3PutRequests;
import com.heuristiq.documents.storage.encryption.EncryptionCapability;
import com.heuristiq.documents.storage.encryption.StorageEncryptionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class MinioStorageProvider implements StorageProvider {

    private final DmsProperties properties;
    private final S3ClientFactory clientFactory;

    @Autowired
    public MinioStorageProvider(DmsProperties properties) {
        this(properties, new S3ClientFactory(properties.getStorage()));
    }

    /** For tests: lets a fake client factory stand in for the real S3 client. */
    public MinioStorageProvider(DmsProperties properties, S3ClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    /**
     * SSE-S3 and SSE-KMS are sent as the standard S3 headers, which the AWS SDK client supports and MinIO accepts when
     * its server-side KMS (KES) is configured; without that MinIO answers the upload with an error.
     */
    @Override
    public EncryptionCapability encryptionCapability() {
        return EncryptionCapability.SERVER_SIDE;
    }

    @Override
    public boolean supports(String provider) {
        return "minio".equalsIgnoreCase(provider) || "s3-compatible".equalsIgnoreCase(provider);
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file) {
        DmsProperties.Minio minio = properties.getStorage().getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw DmsException.badRequest("MinIO endpoint is not configured");
        }
        MessageDigest digest = StorageDigests.sha256();
        try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            PutObjectResponse response = clientFactory.client(minio, URI.create(minio.getEndpoint())).putObject(
                    S3PutRequests.build(minio.getBucketName(), objectKey, file.getContentType(), file.getSize(),
                            properties.getStorage().getEncryption()),
                    RequestBody.fromInputStream(input, file.getSize())
            );
            return new StoredObject(objectKey, response.versionId(), file.getSize(), file.getContentType(),
                    StorageDigests.hex(digest), StorageEncryptionPolicy.recordedEncryption(
                            EncryptionCapability.SERVER_SIDE, properties.getStorage().getEncryption().getMode()));
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to read document");
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to upload document to MinIO");
        }
    }

    @Override
    public Resource load(String objectKey) {
        DmsProperties.Minio minio = properties.getStorage().getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw DmsException.badRequest("MinIO endpoint is not configured");
        }
        try {
            ResponseInputStream<GetObjectResponse> input = clientFactory.client(minio, URI.create(minio.getEndpoint())).getObject(
                    GetObjectRequest.builder().bucket(minio.getBucketName()).key(objectKey).build()
            );
            return new InputStreamResource(input);
        } catch (Exception ex) {
            throw DmsException.notFound("Document not found");
        }
    }

    @Override
    public void delete(String objectKey) {
        DmsProperties.Minio minio = properties.getStorage().getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw DmsException.badRequest("MinIO endpoint is not configured");
        }
        try {
            clientFactory.client(minio, URI.create(minio.getEndpoint())).deleteObject(DeleteObjectRequest.builder()
                    .bucket(minio.getBucketName())
                    .key(objectKey)
                    .build());
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to delete document from MinIO");
        }
    }

    @Override
    public boolean isHealthy() {
        DmsProperties.Minio minio = properties.getStorage().getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            return false;
        }
        try {
            clientFactory.client(minio, URI.create(minio.getEndpoint()))
                    .headBucket(HeadBucketRequest.builder().bucket(minio.getBucketName()).build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Optional<PresignedDownload> presignDownload(String objectKey, Duration ttl) {
        DmsProperties.Minio minio = properties.getStorage().getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            return Optional.empty();
        }
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(minio.getBucketName()).key(objectKey).build())
                    .build();
            PresignedGetObjectRequest presigned = clientFactory.presigner(minio, URI.create(minio.getEndpoint()))
                    .presignGetObject(presignRequest);
            return Optional.of(new PresignedDownload(presigned.url().toString(), Instant.now().plus(ttl)));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @PreDestroy
    void closeClient() {
        clientFactory.close();
    }
}
