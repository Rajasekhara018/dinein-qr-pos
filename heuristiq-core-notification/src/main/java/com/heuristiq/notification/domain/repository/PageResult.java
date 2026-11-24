package com.railcab.nms.domain.repository;

import java.util.List;

/**
 * Domain-owned pagination result. Repository ports must not depend on Spring Data's
 * {@code Page<T>} -- adapters translate to/from it at the boundary (see the persistence
 * adapters' {@code PaginationMapper}).
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
