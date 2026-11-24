package com.heuristiq.documents.scan;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.exception.DmsException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScanProviderRegistry {

    private final DmsProperties properties;
    private final List<ScanProvider> providers;

    public ScanProviderRegistry(DmsProperties properties, List<ScanProvider> providers) {
        this.properties = properties;
        this.providers = List.copyOf(providers);
    }

    public ScanProvider current() {
        String providerName = properties.getScan().getProvider();
        return providers.stream()
                .filter(candidate -> candidate.supports(providerName))
                .findFirst()
                .orElseThrow(() -> DmsException.badRequest("Unsupported DMS scan provider: " + providerName));
    }
}
