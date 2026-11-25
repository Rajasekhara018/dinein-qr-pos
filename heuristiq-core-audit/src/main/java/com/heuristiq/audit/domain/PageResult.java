package com.heuristiq.audit.domain;

import java.util.List;
import java.util.function.Function;

/**
 * Framework-neutral page of results. The field names mirror Spring Data's {@code Page} JSON
 * (content, totalElements, totalPages, size, number, numberOfElements, first, last, empty) so API consumers see the
 * same shape as in the other services.
 *
 * @param content          the page's items
 * @param totalElements    items across all pages
 * @param totalPages       number of pages
 * @param size             requested page size
 * @param number           zero-based page number
 * @param numberOfElements items on this page
 * @param first            whether this is the first page
 * @param last             whether this is the last page
 * @param empty            whether this page has no items
 * @param <T>              item type
 */
public record PageResult<T>(List<T> content, long totalElements, int totalPages, int size, int number,
                            int numberOfElements, boolean first, boolean last, boolean empty) {

    /**
     * Builds a page from its items and the total.
     *
     * @param content       the items of this page
     * @param totalElements items across all pages
     * @param page          zero-based page number
     * @param size          requested page size
     * @param <T>           item type
     * @return the page
     */
    public static <T> PageResult<T> of(List<T> content, long totalElements, int page, int size) {
        int totalPages = (int) Math.ceil(totalElements / (double) size);
        return new PageResult<>(content, totalElements, totalPages, size, page, content.size(), page == 0,
                page >= totalPages - 1, content.isEmpty());
    }

    /**
     * @param mapper conversion of one item
     * @param <R>    new item type
     * @return the same page with converted items
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), totalElements, totalPages, size, number,
                numberOfElements, first, last, empty);
    }
}
