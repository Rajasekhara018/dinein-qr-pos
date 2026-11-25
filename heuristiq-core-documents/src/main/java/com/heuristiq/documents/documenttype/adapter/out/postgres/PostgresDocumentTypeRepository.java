package com.heuristiq.documents.documenttype.adapter.out.postgres;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** PostgreSQL adapter of the document-type store; same behaviour as the Mongo adapter. */
@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
public class PostgresDocumentTypeRepository implements DocumentTypeRepository {

    private final DocumentTypeJpaRepository repository;

    public PostgresDocumentTypeRepository(DocumentTypeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DocumentType> findById(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).map(DocumentTypeJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentType> findByCode(String tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(DocumentTypeJpaEntity::toDomain);
    }

    @Override
    public DocumentTypeRepository.Page list(String tenantId, Query query) {
        DocumentTypeJpaEntity probe = DocumentTypeJpaEntity.probe(tenantId, query.category(), query.flow(), query.active());
        org.springframework.data.domain.Page<DocumentTypeJpaEntity> page = repository.findAll(Example.of(probe),
                PageRequest.of(query.page(), query.size(), Sort.by("code").ascending()));
        return new DocumentTypeRepository.Page(page.getContent().stream().map(DocumentTypeJpaEntity::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public DocumentType save(DocumentType documentType) {
        try {
            return repository.save(DocumentTypeJpaEntity.fromDomain(documentType)).toDomain();
        } catch (OptimisticLockingFailureException e) {
            throw DocumentTypeException.conflict("The document type changed while it was being saved");
        } catch (DataIntegrityViolationException e) {
            throw DocumentTypeException.conflict("A document type with code " + documentType.code() + " already exists");
        }
    }
}
