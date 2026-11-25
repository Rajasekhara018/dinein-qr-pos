package com.heuristiq.identity.orgstructure.adapter.out;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Small helpers shared by the JDBC adapters of the organization structure. */
final class SqlParams {
    private SqlParams() {
    }

    /** Named parameters pre-filled with the tenant as {@code :t}; every query is scoped by it. */
    static MapSqlParameterSource forTenant(String tenantId) {
        return new MapSqlParameterSource("t", tenantId);
    }
}
