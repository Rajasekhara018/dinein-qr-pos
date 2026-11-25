package com.heuristiq.documents.document.audit.mongo;

import com.heuristiq.documents.document.audit.DocumentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataDocumentAuditRepository extends MongoRepository<DocumentAuditEvent, String> {
}
