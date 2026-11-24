package com.railcab.nms.adapters.in.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/**
 * Shared conversion between Spring MVC's {@code Pageable}/{@code Page} and the domain-owned
 * {@link PageQuery}/{@link PageResult} -- keeps the JSON response shape (first/last/
 * numberOfElements/totalPages/etc.) unchanged for existing API consumers, while the
 * application/domain layers never depend on Spring Data pagination types.
 */
public final class WebPaginationSupport {

    private WebPaginationSupport() {
    }

    public static PageQuery toPageQuery(Pageable pageable) {
        return new PageQuery(pageable.getPageNumber(), pageable.getPageSize());
    }

    public static <T> Page<T> toPage(PageResult<T> result, Pageable pageable) {
        return new PageImpl<>(result.content(), pageable, result.totalElements());
    }
}
