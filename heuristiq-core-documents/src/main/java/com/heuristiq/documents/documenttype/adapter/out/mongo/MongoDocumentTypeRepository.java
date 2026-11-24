package com.railcab.dms.documenttype.adapter.out.mongo;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** MongoDB adapter of the document-type store (the default persistence provider). */
@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoDocumentTypeRepository implements DocumentTypeRepository {

    private final SpringDataDocumentTypeRepository repository;

    public MongoDocumentTypeRepository(SpringDataDocumentTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DocumentType> findById(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id).map(DocumentTypeMongoDocument::toDomain);
    }

    @Override
    public Optional<DocumentType> findByCode(String tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(DocumentTypeMongoDocument::toDomain);
    }

    @Override
    public DocumentTypeRepository.Page list(String tenantId, Query query) {
        DocumentTypeMongoDocument probe = DocumentTypeMongoDocument.probe(tenantId, query.category(), query.flow(), query.active());
        org.springframework.data.domain.Page<DocumentTypeMongoDocument> page = repository.findAll(Example.of(probe),
                PageRequest.of(query.page(), query.size(), Sort.by("code").ascending()));
        return new DocumentTypeRepository.Page(page.getContent().stream().map(DocumentTypeMongoDocument::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public DocumentType save(DocumentType documentType) {
        try {
            return repository.save(DocumentTypeMongoDocument.fromDomain(documentType)).toDomain();
        } catch (OptimisticLockingFailureException e) {
            throw DocumentTypeException.conflict("The document type changed while it was being saved");
        } catch (DataIntegrityViolationException e) {
            throw DocumentTypeException.conflict("A document type with code " + documentType.code() + " already exists");
        }
    }
}
