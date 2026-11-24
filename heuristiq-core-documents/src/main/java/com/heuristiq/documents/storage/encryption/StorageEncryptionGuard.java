package com.railcab.dms.storage.encryption;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.storage.StorageProviderRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fails startup when the encryption settings cannot be honoured by the configured storage provider (or when
 * {@code require-encryption} is on and documents would be stored unencrypted), and logs a WARN for settings a
 * provider ignores. See {@link StorageEncryptionPolicy}.
 */
@Component
public class StorageEncryptionGuard {

    private static final Logger log = LoggerFactory.getLogger(StorageEncryptionGuard.class);

    private final DmsProperties properties;
    private final StorageProviderRegistry registry;

    public StorageEncryptionGuard(DmsProperties properties, StorageProviderRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    /** @throws IllegalStateException listing every problem, so the application does not start */
    @PostConstruct
    void verify() {
        DmsProperties.Storage storage = properties.getStorage();
        String provider = registry.currentProviderName();
        EncryptionCapability capability;
        try {
            capability = registry.current().encryptionCapability();
        } catch (DmsException ex) {
            throw new IllegalStateException("Invalid DMS storage configuration: " + ex.getMessage(), ex);
        }
        StorageEncryptionPolicy.Result result = StorageEncryptionPolicy.evaluate(provider,
                capability, storage.getEncryption().getMode(),
                storage.getEncryption().getKmsKeyId(), storage.isRequireEncryption());
        result.warnings().forEach(warning -> log.warn("dms.storage.encryption {}", warning));
        if (!result.valid()) {
            throw new IllegalStateException("Invalid DMS storage encryption configuration: "
                    + String.join("; ", result.errors()));
        }
        log.info("dms.storage.encryption provider={} mode={} requireEncryption={}", provider,
                storage.getEncryption().getMode(), storage.isRequireEncryption());
    }
}
