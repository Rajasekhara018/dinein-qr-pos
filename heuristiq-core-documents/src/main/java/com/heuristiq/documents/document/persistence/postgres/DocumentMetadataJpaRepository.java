package com.heuristiq.documents.document.persistence.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface DocumentMetadataJpaRepository extends JpaRepository<DocumentMetadataJpaEntity, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<DocumentMetadataJpaEntity> {

    Optional<DocumentMetadataJpaEntity> findByTenantIdAndId(String tenantId, String id);

    List<DocumentMetadataJpaEntity> findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(String category, String ownerId);

    Optional<DocumentMetadataJpaEntity> findByIdAndDeletedFalse(String id);

    Optional<DocumentMetadataJpaEntity> findByObjectKeyAndDeletedFalse(String objectKey);

    List<DocumentMetadataJpaEntity> findByDeletedTrueAndDeletedAtBefore(Instant cutoff);

    Page<DocumentMetadataJpaEntity> findByDeletedFalse(Pageable pageable);

    List<DocumentMetadataJpaEntity> findByTenantIdAndOwnerIdInAndCategoryInAndDeletedFalse(String tenantId, Collection<String> ownerIds, Collection<String> categories);

    Page<DocumentMetadataJpaEntity> findByCategoryInAndValidUntilBetweenAndDeletedFalse(Collection<String> categories, LocalDate from, LocalDate to, Pageable pageable);
}
