package com.heuristiq.gateway.privilege;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry in {@code privilege.json} -- the fields the gateway needs for path-based authorization, plus
 * {@code name}, {@code module} and {@code submodule}, which the access-audit publisher records. Other fields
 * (description, type) are ignored here; UAM's copy of this same file is what seeds the full Permission catalog.
 *
 * @param apipath written in BACKEND form (e.g. {@code /api/v1/uam/users/{id}}) -- the path the
 *                target service actually receives after the gateway's rewrite, not the path the
 *                client calls. The version is a literal part of this string (e.g. "v1"), so a v2
 *                counterpart of the same endpoint is simply a separate entry with its own
 *                {@code /api/v2/...} apipath -- there's no separate version-restriction field.
 *                Matched against the projected backend path each of
 *                {@code PrivilegeBasedAccessFilter}/{@code RouteConfig} computes independently for
 *                the incoming request (each keeps its own identical copy of that resolution logic).
 * @param service required disambiguator matching one of those resolvers' prefix-table slugs (e.g.
 *                "admin", "uam"). Every service's own ApiVersionConfig prepends the SAME literal
 *                {@code /api/v1}, so {@code apipath} alone can't tell two services' traffic apart
 *                -- {@code service} is what does, checked alongside the path pattern.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivilegeEntry(int privilegeId, String apipath, String service, String operation,
                             String name, String module, String submodule) {

    /** Entry without descriptive fields (tests and callers that only need path authorization). */
    public PrivilegeEntry(int privilegeId, String apipath, String service, String operation) {
        this(privilegeId, apipath, service, operation, null, null, null);
    }
}
