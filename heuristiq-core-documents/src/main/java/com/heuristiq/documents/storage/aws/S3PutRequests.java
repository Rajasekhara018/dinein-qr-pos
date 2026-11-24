package com.railcab.dms.storage.aws;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.storage.encryption.EncryptionMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Builds the S3 {@code PutObject} request shared by the AWS and MinIO providers, including the server-side
 * encryption fields. Stateless.
 */
public final class S3PutRequests {

    private S3PutRequests() {
    }

    /**
     * @param encryption mode and key from {@code railcab.dms.storage.encryption.*}; NONE adds no encryption field,
     *                   SSE_S3 sets {@code AES256}, SSE_KMS sets {@code aws:kms} and the key id
     */
    public static PutObjectRequest build(String bucket, String key, String contentType, long contentLength,
                                         DmsProperties.Encryption encryption) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength);
        EncryptionMode mode = encryption == null ? EncryptionMode.NONE : encryption.getMode();
        switch (mode) {
            case SSE_S3 -> builder.serverSideEncryption(ServerSideEncryption.AES256);
            case SSE_KMS -> builder.serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(encryption.getKmsKeyId());
            case NONE -> {
                // no encryption header requested
            }
        }
        return builder.build();
    }
}
