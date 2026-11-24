package com.railcab.dms.storage;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.exception.DmsException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageProviderRegistry {

    private final DmsProperties properties;
    private final List<StorageProvider> providers;

    public StorageProviderRegistry(DmsProperties properties, List<StorageProvider> providers) {
        this.properties = properties;
        this.providers = List.copyOf(providers);
    }

    public StorageProvider current() {
        return forProvider(currentProviderName());
    }

    public StorageProvider forProvider(String providerName) {
        return providers.stream()
                .filter(candidate -> candidate.supports(providerName))
                .findFirst()
                .orElseThrow(() -> DmsException.badRequest("Unsupported DMS storage provider: " + providerName));
    }

    public String currentProviderName() {
        return properties.getStorage().getProvider();
    }
}
