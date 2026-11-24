package com.heuristiq.documents.document.persistence;

import com.heuristiq.documents.document.domain.DocumentMetadata;

import com.heuristiq.documents.common.paging.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for document metadata. Business logic depends only on this interface,
 * never on a specific database's client/repository types, so the backing store (MongoDB,
 * PostgreSQL, ...) can be swapped via {@code railcab.dms.persistence.provider} without
 * touching {@code DocumentService} or {@code DocumentRetentionJob}.
 */
public interface DocumentMetadataStore {

    DocumentMetadata save(DocumentMetadata metadata);

    List<DocumentMetadata> findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(String category, String ownerId);

    Optional<DocumentMetadata> findByIdAndDeletedFalse(String id);

    Optional<DocumentMetadata> findByObjectKeyAndDeletedFalse(String objectKey);

    /**
     * One document of an organization, deleted or not. A document of another organization (or without one) is simply
     * absent.
     */
    Optional<DocumentMetadata> findByTenantIdAndId(String tenantId, String id);

    /**
     * Searches the documents of one organization, deleted ones included unless filtered, newest first with the id as
     * tiebreaker.
     *
     * @param page zero-based page
     * @param size page size (the caller caps it)
     */
    PagedResult<DocumentMetadata> search(String tenantId, DocumentSearchCriteria criteria, int page, int size);

    List<DocumentMetadata> findByDeletedTrueAndDeletedAtBefore(Instant cutoff);

    void deleteAll(List<DocumentMetadata> documents);

    /** One page (zero-based) of the documents that are not deleted, in a stable order by id; used to re-index them for search. */
    List<DocumentMetadata> findActivePage(int page, int size);

    /** D12: the not-deleted documents of these owners in these categories, within one organization. */
    List<DocumentMetadata> findByTenantOwnersAndCategories(String tenantId, Collection<String> ownerIds, Collection<String> categories);

    /** D12: one page (zero-based, stable order by id) of the not-deleted documents of these categories valid until a day in [from, to]. */
    List<DocumentMetadata> findValidUntilBetween(Collection<String> categories, LocalDate from, LocalDate to, int page, int size);
}
