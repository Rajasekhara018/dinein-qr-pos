package com.heuristiq.uam.feature.management;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class ManagementPageSupport {

    private ManagementPageSupport() {
    }

    public static Pageable page(int page, int size, String sortField) {
        return page(page, size, sortField, Sort.Direction.ASC);
    }

    /** Same as {@link #page(int, int, String)} with an explicit direction (e.g. newest first for activity feeds). */
    public static Pageable page(int page, int size, String sortField, Sort.Direction direction) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), ManagementConstants.MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(direction, sortField));
    }
}
