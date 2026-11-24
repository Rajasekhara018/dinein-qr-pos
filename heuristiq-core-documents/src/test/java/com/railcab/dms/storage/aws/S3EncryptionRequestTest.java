package com.railcab.dms.storage.aws;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.storage.StorageProvider;
import com.railcab.dms.storage.encryption.EncryptionMode;
import com.railcab.dms.storage.minio.MinioStorageProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The server-side-encryption fields on the S3 and MinIO PutObject request, with a fake S3 client (no network). */
class S3EncryptionRequestTest {

    private static final String KMS_KEY = "arn:aws:kms:ap-south-1:111122223333:key/abcd";
    private final MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", "abc".getBytes());

    private static DmsProperties.Encryption encryption(EncryptionMode mode, String key) {
        DmsProperties.Encryption e = new DmsProperties.Encryption();
        e.setMode(mode);
        e.setKmsKeyId(key);
        return e;
    }

    @Test
    void builderSetsNoEncryptionFieldsForNone() {
        PutObjectRequest request = S3PutRequests.build("b", "k", "application/pdf", 3, encryption(EncryptionMode.NONE, null));

        assertNull(request.serverSideEncryption());
        assertNull(request.ssekmsKeyId());
        assertEquals("b", request.bucket());
        assertEquals("k", request.key());
        assertEquals(3L, request.contentLength());
    }

    @Test
    void builderSetsAes256ForSseS3() {
        PutObjectRequest request = S3PutRequests.build("b", "k", "application/pdf", 3, encryption(EncryptionMode.SSE_S3, null));

        assertEquals(ServerSideEncryption.AES256, request.serverSideEncryption());
        assertNull(request.ssekmsKeyId());
    }

    @Test
    void builderSetsKmsAndKeyForSseKms() {
        PutObjectRequest request = S3PutRequests.build("b", "k", "application/pdf", 3, encryption(EncryptionMode.SSE_KMS, KMS_KEY));

        assertEquals(ServerSideEncryption.AWS_KMS, request.serverSideEncryption());
        assertEquals(KMS_KEY, request.ssekmsKeyId());
    }

    @Test
    void awsProviderSendsConfiguredEncryptionAndRecordsIt() {
        DmsProperties properties = new DmsProperties();
        properties.getStorage().getAwsS3().setBucketName("docs");
        properties.getStorage().getEncryption().setMode(EncryptionMode.SSE_KMS);
        properties.getStorage().getEncryption().setKmsKeyId(KMS_KEY);
        S3Client s3 = mock(S3Client.class);
        S3ClientFactory factory = mock(S3ClientFactory.class);
        when(factory.client(any(), isNull())).thenReturn(s3);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().versionId("v1").build());

        StorageProvider.StoredObject stored = new S3StorageProvider(properties, factory).store("org-a/kyc/general/u1/f.pdf", file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals("docs", request.getValue().bucket());
        assertEquals("org-a/kyc/general/u1/f.pdf", request.getValue().key());
        assertEquals(ServerSideEncryption.AWS_KMS, request.getValue().serverSideEncryption());
        assertEquals(KMS_KEY, request.getValue().ssekmsKeyId());
        assertEquals("SSE_KMS", stored.encryption());
        assertEquals("v1", stored.versionId());
    }

    @Test
    void awsProviderWithNoneSendsNoEncryptionAndRecordsNone() {
        DmsProperties properties = new DmsProperties();
        S3Client s3 = mock(S3Client.class);
        S3ClientFactory factory = mock(S3ClientFactory.class);
        when(factory.client(any(), isNull())).thenReturn(s3);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());

        StorageProvider.StoredObject stored = new S3StorageProvider(properties, factory).store("k", file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertNull(request.getValue().serverSideEncryption());
        assertEquals("NONE", stored.encryption());
    }

    @Test
    void minioProviderSendsSseS3AndRecordsIt() {
        DmsProperties properties = new DmsProperties();
        properties.getStorage().getMinio().setEndpoint("http://minio:9000");
        properties.getStorage().getMinio().setBucketName("docs");
        properties.getStorage().getEncryption().setMode(EncryptionMode.SSE_S3);
        S3Client s3 = mock(S3Client.class);
        S3ClientFactory factory = mock(S3ClientFactory.class);
        when(factory.client(any(), any(URI.class))).thenReturn(s3);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder().build());

        StorageProvider.StoredObject stored = new MinioStorageProvider(properties, factory).store("k", file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertEquals("docs", request.getValue().bucket());
        assertEquals(ServerSideEncryption.AES256, request.getValue().serverSideEncryption());
        assertEquals("SSE_S3", stored.encryption());
    }
}
