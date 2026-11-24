package com.railcab.nms.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/**
 * Shared conversion between the domain-owned pagination contract ({@link PageQuery}/
 * {@link PageResult}) and Spring Data's ({@code Pageable}/{@code Page}) -- used by every JPA/Mongo
 * repository adapter so the domain repository ports never depend on Spring Data types directly.
 */
public final class PaginationMapper {

    private PaginationMapper() {
    }

    public static Pageable toPageable(PageQuery query) {
        return org.springframework.data.domain.PageRequest.of(query.page(), query.size());
    }

    public static <T> PageResult<T> toPageResult(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
