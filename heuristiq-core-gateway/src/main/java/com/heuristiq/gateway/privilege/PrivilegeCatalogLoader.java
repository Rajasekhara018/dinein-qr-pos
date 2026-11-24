package com.heuristiq.gateway.privilege;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Reads {@code privilege.json} off the classpath -- the same file UAM seeds its Permission
 * catalog from (kept in sync manually between the two modules; see UAM's PermissionCatalogLoader).
 */
@Slf4j
@Component
public class PrivilegeCatalogLoader {
    private static final String CATALOG_RESOURCE = "privilege.json";

    private final ObjectMapper objectMapper;

    public PrivilegeCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PrivilegeEntry> load() {
        try (InputStream stream = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readValue(stream, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, PrivilegeEntry.class));
        } catch (IOException e) {
            log.error("Failed to load {} from classpath; privilege-based authorization will allow every request through", CATALOG_RESOURCE, e);
            return List.of();
        }
    }
}
