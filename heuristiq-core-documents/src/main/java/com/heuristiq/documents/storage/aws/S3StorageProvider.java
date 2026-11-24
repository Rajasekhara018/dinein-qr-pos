package com.railcab.dms.storage.aws;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.storage.StorageDigests;
import com.railcab.dms.storage.StorageProvider;
import com.railcab.dms.storage.encryption.EncryptionCapability;
import com.railcab.dms.storage.encryption.StorageEncryptionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class S3StorageProvider implements StorageProvider {

    private final DmsProperties properties;
    private final S3ClientFactory clientFactory;

    @Autowired
    public S3StorageProvider(DmsProperties properties) {
        this(properties, new S3ClientFactory(properties.getStorage()));
    }

    /** For tests: lets a fake client factory stand in for the real AWS client. */
    S3StorageProvider(DmsProperties properties, S3ClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public EncryptionCapability encryptionCapability() {
        return EncryptionCapability.SERVER_SIDE;
    }

    @Override
    public boolean supports(String provider) {
        return "aws".equalsIgnoreCase(provider) || "aws-s3".equalsIgnoreCase(provider) || "s3".equalsIgnoreCase(provider);
    }

    @Override
    public StoredObject store(String objectKey, MultipartFile file) {
        DmsProperties.AwsS3 s3 = properties.getStorage().getAwsS3();
        MessageDigest digest = StorageDigests.sha256();
        try (DigestInputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            PutObjectResponse response = clientFactory.client(s3, null).putObject(
                    S3PutRequests.build(s3.getBucketName(), objectKey, file.getContentType(), file.getSize(),
                            properties.getStorage().getEncryption()),
                    RequestBody.fromInputStream(input, file.getSize())
            );
            return new StoredObject(objectKey, response.versionId(), file.getSize(), file.getContentType(),
                    StorageDigests.hex(digest), StorageEncryptionPolicy.recordedEncryption(
                            EncryptionCapability.SERVER_SIDE, properties.getStorage().getEncryption().getMode()));
        } catch (IOException ex) {
            throw DmsException.badRequest("Unable to read document");
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to upload document to S3");
        }
    }

    @Override
    public Resource load(String objectKey) {
        DmsProperties.AwsS3 s3 = properties.getStorage().getAwsS3();
        try {
            ResponseInputStream<GetObjectResponse> input = clientFactory.client(s3, null).getObject(
                    GetObjectRequest.builder().bucket(s3.getBucketName()).key(objectKey).build()
            );
            return new InputStreamResource(input);
        } catch (Exception ex) {
            throw DmsException.notFound("Document not found");
        }
    }

    @Override
    public void delete(String objectKey) {
        DmsProperties.AwsS3 s3 = properties.getStorage().getAwsS3();
        try {
            clientFactory.client(s3, null).deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3.getBucketName())
                    .key(objectKey)
                    .build());
        } catch (Exception ex) {
            throw DmsException.badRequest("Unable to delete document from S3");
        }
    }

    @Override
    public boolean isHealthy() {
        DmsProperties.AwsS3 s3 = properties.getStorage().getAwsS3();
        try {
            clientFactory.client(s3, null).headBucket(HeadBucketRequest.builder().bucket(s3.getBucketName()).build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Optional<PresignedDownload> presignDownload(String objectKey, Duration ttl) {
        DmsProperties.AwsS3 s3 = properties.getStorage().getAwsS3();
        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(s3.getBucketName()).key(objectKey).build())
                    .build();
            PresignedGetObjectRequest presigned = clientFactory.presigner(s3, null).presignGetObject(presignRequest);
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
