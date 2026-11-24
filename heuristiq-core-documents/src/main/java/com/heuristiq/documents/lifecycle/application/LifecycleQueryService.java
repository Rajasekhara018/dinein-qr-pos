package com.railcab.dms.lifecycle.application;

import com.railcab.dms.common.paging.PagedResult;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.exception.DmsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Read-only troubleshooting queries over the current state of documents. Every call is limited to the one
 * organization passed in (the caller's session organization); another organization's document is reported exactly like
 * a missing one. Page sizes are capped.
 */
@Service
public class LifecycleQueryService {

    private final DocumentMetadataStore documents;

    public LifecycleQueryService(DocumentMetadataStore documents) {
        this.documents = documents;
    }

    /**
     * Searches the organization's documents (deleted included unless filtered).
     *
     * @throws DmsException 400 when the organization is missing or a text filter is too long
     */
    public PagedResult<DocumentMetadata> searchDocuments(String tenantId, DocumentSearchCriteria criteria, Integer page, Integer size) {
        requireTenant(tenantId);
        Objects.requireNonNull(criteria, "criteria");
        requireLength("query", criteria.query(), LifecycleConstants.MAX_QUERY_LENGTH);
        requireLength("ownerId", criteria.ownerId(), LifecycleConstants.MAX_FILTER_LENGTH);
        requireLength("category", criteria.category(), LifecycleConstants.MAX_FILTER_LENGTH);
        requireLength("documentType", criteria.documentType(), LifecycleConstants.MAX_FILTER_LENGTH);
        requireLength("status", criteria.status(), LifecycleConstants.MAX_FILTER_LENGTH);
        requireLength("scanStatus", criteria.scanStatus(), LifecycleConstants.MAX_FILTER_LENGTH);
        requireRange(criteria.from(), criteria.to());
        return documents.search(tenantId, criteria, safePage(page), safeSize(size));
    }

    /**
     * One document of the organization, deleted or not.
     *
     * @throws DmsException 404 when it does not exist or belongs to another organization (same answer for both)
     */
    public DocumentMetadata getDocument(String tenantId, String id) {
        requireTenant(tenantId);
        if (!StringUtils.hasText(id)) {
            throw notFound();
        }
        return documents.findByTenantIdAndId(tenantId, id).orElseThrow(LifecycleQueryService::notFound);
    }

    static int safePage(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    static int safeSize(Integer size) {
        return size == null ? LifecycleConstants.DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), LifecycleConstants.MAX_PAGE_SIZE);
    }

    private static void requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw DmsException.badRequest(LifecycleConstants.NO_ORGANIZATION_MESSAGE);
        }
    }

    private static void requireLength(String name, String value, int max) {
        if (value != null && value.length() > max) {
            throw DmsException.badRequest(name + " is longer than " + max + " characters");
        }
    }

    private static void requireRange(java.time.Instant from, java.time.Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw DmsException.badRequest("from must not be after to");
        }
    }

    private static DmsException notFound() {
        return DmsException.notFound(LifecycleConstants.DOCUMENT_NOT_FOUND_MESSAGE);
    }
}
