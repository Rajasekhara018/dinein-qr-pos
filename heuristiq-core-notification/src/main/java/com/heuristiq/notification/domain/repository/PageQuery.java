package com.heuristiq.notification.domain.repository;

/**
 * Domain-owned pagination request. Repository ports must not depend on Spring Data's
 * {@code Pageable} -- adapters translate to/from it at the boundary (see the persistence
 * adapters' {@code PaginationMapper}); web controllers translate from their own
 * {@code Pageable} (a framework-facing adapter concept, not a domain concern) into this.
 *
 * <p>It also carries the organization {@link TenantScope} of the read, so every paged query is limited the
 * same way without changing each port method; a query built without one is unrestricted.
 */
public record PageQuery(int page, int size, TenantScope scope) {

    public PageQuery {
        scope = scope == null ? TenantScope.UNRESTRICTED : scope;
    }

    public PageQuery(int page, int size) {
        this(page, size, TenantScope.UNRESTRICTED);
    }

    /** The same page request limited to the given organization scope. */
    public PageQuery withScope(TenantScope newScope) {
        return new PageQuery(page, size, newScope);
    }
}
