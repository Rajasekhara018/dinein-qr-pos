package com.heuristiq.documents.document.audit.mongo;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.audit.DocumentAuditEvent;
import com.heuristiq.documents.document.audit.DocumentAuditStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_MONGO, matchIfMissing = true)
public class MongoDocumentAuditStore implements DocumentAuditStore {

    private final SpringDataDocumentAuditRepository repository;

    public MongoDocumentAuditStore(SpringDataDocumentAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentAuditEvent save(DocumentAuditEvent event) {
        return repository.save(event);
    }
}
