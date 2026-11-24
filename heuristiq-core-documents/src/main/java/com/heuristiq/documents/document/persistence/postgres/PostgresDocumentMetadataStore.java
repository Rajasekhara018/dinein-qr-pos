package com.railcab.dms.document.persistence.postgres;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.railcab.dms.common.paging.PagedResult;
import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
public class PostgresDocumentMetadataStore implements DocumentMetadataStore {

    private final DocumentMetadataJpaRepository repository;

    public PostgresDocumentMetadataStore(DocumentMetadataJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentMetadata save(DocumentMetadata metadata) {
        if (!StringUtils.hasText(metadata.getId())) {
            metadata.setId(UUID.randomUUID().toString());
        }
        DocumentMetadataJpaEntity saved = repository.save(DocumentMetadataJpaEntity.fromDomain(metadata));
        return saved.toDomain();
    }

    @Override
    public List<DocumentMetadata> findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(String category, String ownerId) {
        return repository.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(category, ownerId)
                .stream().map(DocumentMetadataJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<DocumentMetadata> findByIdAndDeletedFalse(String id) {
        return repository.findByIdAndDeletedFalse(id).map(DocumentMetadataJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentMetadata> findByObjectKeyAndDeletedFalse(String objectKey) {
        return repository.findByObjectKeyAndDeletedFalse(objectKey).map(DocumentMetadataJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentMetadata> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).map(DocumentMetadataJpaEntity::toDomain);
    }

    @Override
    public PagedResult<DocumentMetadata> search(String tenantId, DocumentSearchCriteria criteria, int page, int size) {
        Page<DocumentMetadataJpaEntity> result = repository.findAll(DocumentSearchSpecifications.forTenant(tenantId, criteria),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PagedResult<>(result.getContent().stream().map(DocumentMetadataJpaEntity::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public List<DocumentMetadata> findByDeletedTrueAndDeletedAtBefore(Instant cutoff) {
        return repository.findByDeletedTrueAndDeletedAtBefore(cutoff)
                .stream().map(DocumentMetadataJpaEntity::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findActivePage(int page, int size) {
        return repository.findByDeletedFalse(PageRequest.of(page, size, Sort.by("id")))
                .stream().map(DocumentMetadataJpaEntity::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findByTenantOwnersAndCategories(String tenantId, java.util.Collection<String> ownerIds,
                                                                  java.util.Collection<String> categories) {
        return repository.findByTenantIdAndOwnerIdInAndCategoryInAndDeletedFalse(tenantId, ownerIds, categories)
                .stream().map(DocumentMetadataJpaEntity::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findValidUntilBetween(java.util.Collection<String> categories, java.time.LocalDate from,
                                                        java.time.LocalDate to, int page, int size) {
        return repository.findByCategoryInAndValidUntilBetweenAndDeletedFalse(categories, from, to, PageRequest.of(page, size, Sort.by("id")))
                .stream().map(DocumentMetadataJpaEntity::toDomain).toList();
    }

    @Override
    public void deleteAll(List<DocumentMetadata> documents) {
        repository.deleteAllById(documents.stream().map(DocumentMetadata::getId).toList());
    }
}
