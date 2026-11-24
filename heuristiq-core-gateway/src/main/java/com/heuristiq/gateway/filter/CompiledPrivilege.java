package com.heuristiq.gateway.filter;

import com.heuristiq.gateway.privilege.PrivilegeEntry;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.Objects;
import java.util.Set;

/**
 * One {@code privilege.json} entry prepared for matching: the path pattern pre-compiled from apipath (path variables
 * like {id} become * for AntPathMatcher), the set of HTTP methods "operation" maps to (null = any method, e.g. MANAGE
 * entries), and the required "service" disambiguator (see {@link PrivilegeEntry#service()}).
 */
final class CompiledPrivilege {

    final int privilegeId;
    final String pattern;
    final String service;
    final String module;
    final Set<HttpMethod> methods;

    private CompiledPrivilege(int privilegeId, String pattern, String service, String module, Set<HttpMethod> methods) {
        this.privilegeId = privilegeId;
        this.pattern = pattern;
        this.service = service;
        this.module = module;
        this.methods = methods;
    }

    static CompiledPrivilege from(PrivilegeEntry entry) {
        String pattern = entry.apipath().replaceAll("\\{[^/]+}", "*");
        return new CompiledPrivilege(entry.privilegeId(), pattern, entry.service(), entry.module(), methodsFor(entry.operation()));
    }

    /** True when this entry allows the request method (or any method). */
    boolean allows(HttpMethod requestMethod) {
        return methods == null || methods.contains(requestMethod);
    }

    boolean matches(AntPathMatcher matcher, String uri, HttpMethod requestMethod, String requestService) {
        if (!allows(requestMethod)) {
            return false;
        }
        // A null/blank service on the entry matches only a service-less (unresolved-prefix)
        // request, and vice versa -- never falls back to "match anything", since that would
        // silently reopen the exact cross-service collision this field exists to prevent.
        if (!Objects.equals(service, requestService)) {
            return false;
        }
        return matcher.match(pattern, uri);
    }

    private static Set<HttpMethod> methodsFor(String operation) {
        if (operation == null) {
            return null;
        }
        return switch (operation) {
            case "CREATE" -> Set.of(HttpMethod.POST);
            case "READ", "LIST" -> Set.of(HttpMethod.GET);
            case "UPDATE" -> Set.of(HttpMethod.PUT, HttpMethod.PATCH);
            case "DELETE" -> Set.of(HttpMethod.DELETE);
            // MANAGE and anything else -> match any method for this path pattern
            default -> null;
        };
    }
}
