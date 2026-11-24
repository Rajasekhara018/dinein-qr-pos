package com.railcab.dms.document.audit.mongo;

import com.railcab.dms.document.audit.DocumentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataDocumentAuditRepository extends MongoRepository<DocumentAuditEvent, String> {
}
