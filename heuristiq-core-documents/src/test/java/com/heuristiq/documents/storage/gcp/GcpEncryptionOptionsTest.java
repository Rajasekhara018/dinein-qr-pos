package com.heuristiq.documents.storage.gcp;

import com.google.cloud.storage.Storage;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.storage.encryption.EncryptionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpEncryptionOptionsTest {

    @Test
    void customerManagedKeyOnlyForSseKms() {
        DmsProperties properties = new DmsProperties();
        GcpStorageProvider provider = new GcpStorageProvider(properties);
        assertTrue(provider.writeOptions().isEmpty());

        properties.getStorage().getEncryption().setMode(EncryptionMode.SSE_S3);
        assertTrue(provider.writeOptions().isEmpty());

        properties.getStorage().getEncryption().setMode(EncryptionMode.SSE_KMS);
        properties.getStorage().getEncryption().setKmsKeyId("projects/p/locations/l/keyRings/r/cryptoKeys/k");
        assertEquals(1, provider.writeOptions().size());
        assertEquals(Storage.BlobWriteOption.kmsKeyName("projects/p/locations/l/keyRings/r/cryptoKeys/k"),
                provider.writeOptions().get(0));
    }
}
