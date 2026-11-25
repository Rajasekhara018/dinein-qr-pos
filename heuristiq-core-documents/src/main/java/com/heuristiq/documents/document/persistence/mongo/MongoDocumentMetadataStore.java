package com.heuristiq.documents.document.persistence.mongo;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.heuristiq.documents.common.paging.PagedResult;
import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoDocumentMetadataStore implements DocumentMetadataStore {

    private final SpringDataDocumentMetadataRepository repository;
    private final MongoTemplate mongoTemplate;

    public MongoDocumentMetadataStore(SpringDataDocumentMetadataRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public DocumentMetadata save(DocumentMetadata metadata) {
        DocumentMetadataMongoDocument saved = repository.save(DocumentMetadataMongoDocument.fromDomain(metadata));
        return saved.toDomain();
    }

    @Override
    public List<DocumentMetadata> findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(String category, String ownerId) {
        return repository.findByCategoryAndOwnerIdAndDeletedFalseOrderByCreatedAtDesc(category, ownerId)
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
    }

    @Override
    public Optional<DocumentMetadata> findByIdAndDeletedFalse(String id) {
        return repository.findByIdAndDeletedFalse(id).map(DocumentMetadataMongoDocument::toDomain);
    }

    @Override
    public Optional<DocumentMetadata> findByObjectKeyAndDeletedFalse(String objectKey) {
        return repository.findByObjectKeyAndDeletedFalse(objectKey).map(DocumentMetadataMongoDocument::toDomain);
    }

    @Override
    public Optional<DocumentMetadata> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).map(DocumentMetadataMongoDocument::toDomain);
    }

    @Override
    public PagedResult<DocumentMetadata> search(String tenantId, DocumentSearchCriteria criteria, int page, int size) {
        Query filter = MongoDocumentSearchQueries.build(tenantId, criteria);
        long total = mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), DocumentMetadataMongoDocument.class);
        List<DocumentMetadata> rows = mongoTemplate.find(
                        Query.of(filter).with(PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))),
                        DocumentMetadataMongoDocument.class)
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
        return new PagedResult<>(rows, page, size, total);
    }

    @Override
    public List<DocumentMetadata> findByDeletedTrueAndDeletedAtBefore(Instant cutoff) {
        return repository.findByDeletedTrueAndDeletedAtBefore(cutoff)
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findActivePage(int page, int size) {
        return repository.findByDeletedFalse(PageRequest.of(page, size, Sort.by("id")))
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findByTenantOwnersAndCategories(String tenantId, java.util.Collection<String> ownerIds,
                                                                  java.util.Collection<String> categories) {
        return repository.findByTenantIdAndOwnerIdInAndCategoryInAndDeletedFalse(tenantId, ownerIds, categories)
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
    }

    @Override
    public List<DocumentMetadata> findValidUntilBetween(java.util.Collection<String> categories, java.time.LocalDate from,
                                                        java.time.LocalDate to, int page, int size) {
        return repository.findByCategoryInAndValidUntilBetweenAndDeletedFalse(categories, from, to, PageRequest.of(page, size, Sort.by("id")))
                .stream().map(DocumentMetadataMongoDocument::toDomain).toList();
    }

    @Override
    public void deleteAll(List<DocumentMetadata> documents) {
        repository.deleteAll(documents.stream().map(DocumentMetadataMongoDocument::fromDomain).toList());
    }
}
