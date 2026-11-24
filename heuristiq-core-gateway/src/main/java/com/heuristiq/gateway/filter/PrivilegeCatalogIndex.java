package com.heuristiq.gateway.filter;

import com.heuristiq.gateway.privilege.PrivilegeEntry;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The privilege catalog indexed once, at load, by service and HTTP method, so a request only runs the Ant path match
 * against the entries of its own service that allow its method, instead of scanning the whole catalog.
 *
 * <p>The answer is exactly what a scan of the whole catalog with {@link CompiledPrivilege#matches} gives, in the same
 * (catalog) order: each per-service, per-method list keeps catalog order, and the final check is that same
 * {@code matches} call.
 */
final class PrivilegeCatalogIndex {

    private final List<CompiledPrivilege> all;
    /** Service slug (null for entries without a service) to that service's entries by method. */
    private final Map<String, ServiceEntries> byService;

    private PrivilegeCatalogIndex(List<CompiledPrivilege> all, Map<String, ServiceEntries> byService) {
        this.all = all;
        this.byService = byService;
    }

    static PrivilegeCatalogIndex of(List<PrivilegeEntry> entries) {
        List<CompiledPrivilege> compiled = entries.stream().map(CompiledPrivilege::from).toList();
        Map<String, List<CompiledPrivilege>> grouped = new HashMap<>();
        for (CompiledPrivilege entry : compiled) {
            grouped.computeIfAbsent(entry.service, s -> new ArrayList<>()).add(entry);
        }
        Map<String, ServiceEntries> byService = new HashMap<>();
        grouped.forEach((service, list) -> byService.put(service, ServiceEntries.of(list)));
        return new PrivilegeCatalogIndex(compiled, Collections.unmodifiableMap(byService));
    }

    static PrivilegeCatalogIndex empty() {
        return of(List.of());
    }

    int size() {
        return all.size();
    }

    /** Every entry, in catalog order. */
    List<CompiledPrivilege> all() {
        return all;
    }

    /** The catalog entries matching this request, in catalog order. */
    List<CompiledPrivilege> matching(AntPathMatcher matcher, String uri, HttpMethod method, String service) {
        ServiceEntries entries = byService.get(service);
        if (entries == null) {
            return List.of();
        }
        List<CompiledPrivilege> result = null;
        for (CompiledPrivilege entry : entries.forMethod(method)) {
            if (entry.matches(matcher, uri, method, service)) {
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(entry);
            }
        }
        return result == null ? List.of() : result;
    }

    /** One service's entries: per standard HTTP method, the entries allowing it; plus the any-method entries. */
    private record ServiceEntries(Map<HttpMethod, List<CompiledPrivilege>> byMethod, List<CompiledPrivilege> anyMethod) {

        static ServiceEntries of(List<CompiledPrivilege> entries) {
            Map<HttpMethod, List<CompiledPrivilege>> byMethod = new HashMap<>();
            for (HttpMethod method : HttpMethod.values()) {
                byMethod.put(method, entries.stream().filter(e -> e.allows(method)).toList());
            }
            List<CompiledPrivilege> anyMethod = entries.stream().filter(e -> e.methods == null).toList();
            return new ServiceEntries(Map.copyOf(byMethod), anyMethod);
        }

        /** Entries that allow {@code method}; a non-standard method can only match the any-method entries. */
        List<CompiledPrivilege> forMethod(HttpMethod method) {
            if (method == null) {
                return anyMethod;
            }
            return byMethod.getOrDefault(method, anyMethod);
        }
    }
}
