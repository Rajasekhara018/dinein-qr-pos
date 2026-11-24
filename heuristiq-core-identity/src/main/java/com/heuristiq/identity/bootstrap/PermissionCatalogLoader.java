package com.heuristiq.uam.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;

/**
 * Reads the full permission catalog from the classpath JSON resource so it can be seeded into
 * the database on startup (see {@link UamBootstrapRunner}) instead of living as a hardcoded
 * Java list — makes it easy to review/extend the full set of permissions in one place.
 *
 * <p>Loads {@code privilege.json} -- the single source of truth for both UAM's Permission catalog
 * and the ApiGateway's own path-based authorization. Shared entries are provisioned at startup;
 * business entries are selected by the application's catalogue prefix when it is registered.
 */
@Component
public class PermissionCatalogLoader {

    private final ObjectMapper objectMapper;

    public PermissionCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PermissionCatalogEntry> load() {
        try (InputStream stream = new ClassPathResource(BootstrapRoleConstants.CATALOG_RESOURCE).getInputStream()) {
            List<PermissionCatalogEntry> entries = objectMapper.readValue(stream, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, PermissionCatalogEntry.class));
            if (entries.isEmpty() || entries.stream().anyMatch(entry -> entry.privilegeCode() == null || entry.privilegeCode().isBlank())) {
                throw new IllegalStateException("Privilege catalog is empty or missing readable codes");
            }
            return List.copyOf(entries);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot provision permissions: failed to load privilege.json", e);
        }
    }

    public List<PermissionCatalogEntry> loadShared() {
        return load().stream().filter(entry -> entry.privilegeCode().startsWith("SHARED_")).toList();
    }

    public List<PermissionCatalogEntry> loadApplication(String applicationCode) {
        String prefix = ApplicationCatalogue.find(applicationCode).map(entry -> entry.privilegePrefix()).orElse(null);
        if (prefix == null) return List.of();
        return load().stream().filter(entry -> entry.privilegeCode().startsWith(prefix + "_")).toList();
    }
}
