package com.railcab.dms.documenttype.adapter.in;

import java.util.List;

/** One page of document types. */
public record DocumentTypePageResponse(List<DocumentTypeResponse> content, int page, int size, long totalElements,
                                       int totalPages) {
}
