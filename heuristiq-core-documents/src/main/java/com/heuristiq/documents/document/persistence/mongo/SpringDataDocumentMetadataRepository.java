package com.railcab.dms.document.persistence.mongo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataDocumentMetadataRepository extends MongoRepository<DocumentMetadataMongoDocument, String> {

    List<DocumentMetadataMongoDocument> findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(String category, String ownerId);

    Optional<DocumentMetadataMongoDocument> findByTenantIdAndId(String tenantId, String id);

    Optional<DocumentMetadataMongoDocument> findByIdAndDeletedFalse(String id);

    Optional<DocumentMetadataMongoDocument> findByObjectKeyAndDeletedFalse(String objectKey);

    List<DocumentMetadataMongoDocument> findByDeletedTrueAndDeletedAtBefore(Instant cutoff);

    Page<DocumentMetadataMongoDocument> findByDeletedFalse(Pageable pageable);

    List<DocumentMetadataMongoDocument> findByTenantIdAndOwnerIdInAndCategoryInAndDeletedFalse(String tenantId, Collection<String> ownerIds, Collection<String> categories);

    Page<DocumentMetadataMongoDocument> findByCategoryInAndValidUntilBetweenAndDeletedFalse(Collection<String> categories, LocalDate from, LocalDate to, Pageable pageable);
}
