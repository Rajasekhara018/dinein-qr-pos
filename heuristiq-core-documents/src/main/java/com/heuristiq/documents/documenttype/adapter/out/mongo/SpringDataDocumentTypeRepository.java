package com.heuristiq.documents.documenttype.adapter.out.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/** Spring Data access to {@code dms_document_types}; every finder carries the organization. */
interface SpringDataDocumentTypeRepository extends MongoRepository<DocumentTypeMongoDocument, String> {

    Optional<DocumentTypeMongoDocument> findByTenantIdAndId(String tenantId, String id);

    Optional<DocumentTypeMongoDocument> findByTenantIdAndCode(String tenantId, String code);
}
