package com.heuristiq.documents.storage.encryption;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.storage.StorageProvider;
import com.heuristiq.documents.storage.StorageProviderRegistry;
import com.heuristiq.documents.storage.aws.S3StorageProvider;
import com.heuristiq.documents.storage.azure.AzureBlobStorageProvider;
import com.heuristiq.documents.storage.gcp.GcpStorageProvider;
import com.heuristiq.documents.storage.local.LocalStorageProvider;
import com.heuristiq.documents.storage.minio.MinioStorageProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mode x provider x require-encryption matrix, and the startup guard that applies it. */
class StorageEncryptionPolicyTest {

    private static final String KEY = "arn:aws:kms:ap-south-1:1:key/abc";

    private static StorageEncryptionPolicy.Result eval(EncryptionCapability cap, EncryptionMode mode, String key, boolean require) {
        return StorageEncryptionPolicy.evaluate("p", cap, mode, key, require);
    }

    @Test
    void localAcceptsOnlyNone() {
        assertTrue(eval(EncryptionCapability.PLAINTEXT_ONLY, EncryptionMode.NONE, null, false).valid());
        assertFalse(eval(EncryptionCapability.PLAINTEXT_ONLY, EncryptionMode.SSE_S3, null, false).valid());
        assertFalse(eval(EncryptionCapability.PLAINTEXT_ONLY, EncryptionMode.SSE_KMS, KEY, false).valid());
    }

    @Test
    void s3AndMinioHonourEveryModeButKmsNeedsAKey() {
        assertTrue(eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.NONE, null, false).valid());
        assertTrue(eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_S3, null, false).valid());
        assertTrue(eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_KMS, KEY, false).valid());
        assertFalse(eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_KMS, " ", false).valid());
        assertFalse(eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_KMS, null, false).valid());
    }

    @Test
    void azureRejectsKmsAndWarnsOnSseS3() {
        assertFalse(eval(EncryptionCapability.PROVIDER_MANAGED, EncryptionMode.SSE_KMS, KEY, false).valid());
        var sseS3 = eval(EncryptionCapability.PROVIDER_MANAGED, EncryptionMode.SSE_S3, null, false);
        assertTrue(sseS3.valid());
        assertEquals(1, sseS3.warnings().size());
        assertTrue(eval(EncryptionCapability.PROVIDER_MANAGED, EncryptionMode.NONE, null, false).warnings().isEmpty());
    }

    @Test
    void gcpAcceptsKmsAndWarnsOnSseS3() {
        assertTrue(eval(EncryptionCapability.PROVIDER_MANAGED_WITH_KMS, EncryptionMode.SSE_KMS, KEY, false).valid());
        assertEquals(1, eval(EncryptionCapability.PROVIDER_MANAGED_WITH_KMS, EncryptionMode.SSE_S3, null, false).warnings().size());
    }

    @Test
    void ignoredKeyIsAWarning() {
        var result = eval(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_S3, KEY, false);
        assertTrue(result.valid());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void requireEncryptionDemandsANonLocalProviderAndAModeOtherThanNone() {
        for (EncryptionCapability cap : EncryptionCapability.values()) {
            for (EncryptionMode mode : EncryptionMode.values()) {
                String key = mode == EncryptionMode.SSE_KMS ? KEY : null;
                boolean expectedOk = cap != EncryptionCapability.PLAINTEXT_ONLY && mode != EncryptionMode.NONE
                        && !(cap == EncryptionCapability.PROVIDER_MANAGED && mode == EncryptionMode.SSE_KMS);
                assertEquals(expectedOk, eval(cap, mode, key, true).valid(), cap + " x " + mode + " with require");
            }
        }
    }

    @Test
    void recordedEncryptionDescribesWhatTheProviderDid() {
        assertEquals("NONE", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.PLAINTEXT_ONLY, null));
        assertEquals("SSE_S3", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.SERVER_SIDE, EncryptionMode.SSE_S3));
        assertEquals("NONE", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.SERVER_SIDE, EncryptionMode.NONE));
        assertEquals("SSE_KMS", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.PROVIDER_MANAGED_WITH_KMS, EncryptionMode.SSE_KMS));
        assertEquals("PROVIDER_MANAGED", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.PROVIDER_MANAGED_WITH_KMS, EncryptionMode.NONE));
        assertEquals("PROVIDER_MANAGED", StorageEncryptionPolicy.recordedEncryption(EncryptionCapability.PROVIDER_MANAGED, EncryptionMode.SSE_S3));
    }

    // ------------------------------------------------------------------------------------------- guard + providers

    private static StorageEncryptionGuard guard(String provider, EncryptionMode mode, String key, boolean require) {
        DmsProperties properties = new DmsProperties();
        properties.getStorage().setProvider(provider);
        properties.getStorage().getEncryption().setMode(mode);
        properties.getStorage().getEncryption().setKmsKeyId(key);
        properties.getStorage().setRequireEncryption(require);
        List<StorageProvider> providers = List.of(new LocalStorageProvider(properties), new S3StorageProvider(properties),
                new MinioStorageProvider(properties), new AzureBlobStorageProvider(properties), new GcpStorageProvider(properties));
        return new StorageEncryptionGuard(properties, new StorageProviderRegistry(properties, providers));
    }

    @Test
    void guardMatrixPerRealProvider() {
        assertDoesNotThrow(() -> guard("local", EncryptionMode.NONE, null, false).verify());
        assertThrows(IllegalStateException.class, () -> guard("local", EncryptionMode.SSE_S3, null, false).verify());
        assertThrows(IllegalStateException.class, () -> guard("local", EncryptionMode.NONE, null, true).verify());
        assertThrows(IllegalStateException.class, () -> guard("s3", EncryptionMode.NONE, null, true).verify());
        assertDoesNotThrow(() -> guard("s3", EncryptionMode.SSE_S3, null, true).verify());
        assertDoesNotThrow(() -> guard("aws-s3", EncryptionMode.SSE_KMS, KEY, true).verify());
        assertThrows(IllegalStateException.class, () -> guard("s3", EncryptionMode.SSE_KMS, null, false).verify());
        assertDoesNotThrow(() -> guard("minio", EncryptionMode.SSE_S3, null, true).verify());
        assertThrows(IllegalStateException.class, () -> guard("azure", EncryptionMode.SSE_KMS, KEY, false).verify());
        assertDoesNotThrow(() -> guard("azure", EncryptionMode.SSE_S3, null, true).verify());
        assertDoesNotThrow(() -> guard("gcp", EncryptionMode.SSE_KMS, "projects/p/locations/l/keyRings/r/cryptoKeys/k", true).verify());
        assertThrows(IllegalStateException.class, () -> guard("nonsense", EncryptionMode.NONE, null, false).verify());
    }

    @Test
    void guardMessageNamesTheProblemAndTheSetting() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard("local", EncryptionMode.NONE, null, true).verify());
        assertTrue(ex.getMessage().contains("require-encryption"), ex.getMessage());
        assertTrue(ex.getMessage().contains("local"), ex.getMessage());
    }

    @Test
    void defaultsAreNoneAndNotRequired() {
        DmsProperties properties = new DmsProperties();
        assertEquals(EncryptionMode.NONE, properties.getStorage().getEncryption().getMode());
        assertFalse(properties.getStorage().isRequireEncryption());
        assertDoesNotThrow(() -> guard("local", EncryptionMode.NONE, null, false).verify());
    }

    @Test
    void everyProviderDeclaresItsCapability() {
        DmsProperties properties = new DmsProperties();
        assertEquals(EncryptionCapability.PLAINTEXT_ONLY, new LocalStorageProvider(properties).encryptionCapability());
        assertEquals(EncryptionCapability.SERVER_SIDE, new S3StorageProvider(properties).encryptionCapability());
        assertEquals(EncryptionCapability.SERVER_SIDE, new MinioStorageProvider(properties).encryptionCapability());
        assertEquals(EncryptionCapability.PROVIDER_MANAGED, new AzureBlobStorageProvider(properties).encryptionCapability());
        assertEquals(EncryptionCapability.PROVIDER_MANAGED_WITH_KMS, new GcpStorageProvider(properties).encryptionCapability());
    }
}
