package com.railcab.dms.lifecycle.adapter.in;

import java.util.List;

/** One page of results. */
public record LifecyclePageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
