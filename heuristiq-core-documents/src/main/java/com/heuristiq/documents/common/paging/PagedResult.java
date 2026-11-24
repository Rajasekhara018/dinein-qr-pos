package com.railcab.dms.common.paging;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results as returned by a persistence port (zero-based page).
 *
 * @param content       the items of this page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements total matching items
 */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements) {

    /** Number of pages for {@code totalElements} at {@code size}. */
    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    /** The same page with every item converted. */
    public <R> PagedResult<R> map(Function<T, R> mapper) {
        return new PagedResult<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }
}
