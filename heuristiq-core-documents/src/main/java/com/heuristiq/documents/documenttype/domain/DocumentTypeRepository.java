package com.railcab.dms.documenttype.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port to the store of document types. Every method takes the organization and returns only that organization's
 * records; there is deliberately no lookup without a tenant.
 */
public interface DocumentTypeRepository {

    Optional<DocumentType> findById(String tenantId, String id);

    Optional<DocumentType> findByCode(String tenantId, String code);

    /**
     * A page of the organization's types, sorted by code ascending.
     *
     * @param query filters and paging (size is already capped by the caller)
     */
    Page list(String tenantId, Query query);

    /**
     * Inserts or updates. An update is accepted only against the version that was read.
     *
     * @return the stored record with its new version
     * @throws DocumentTypeException (CONFLICT) when the record changed meanwhile or the code is taken in the organization
     */
    DocumentType save(DocumentType documentType);

    /** Optional filters; null means "any". Paging is zero-based. */
    record Query(String category, String flow, Boolean active, int page, int size) {
    }

    /** One page of results. */
    record Page(List<DocumentType> content, int page, int size, long totalElements, int totalPages) {
    }
}
