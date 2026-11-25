package com.heuristiq.documents.document.audit.postgres;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.document.audit.DocumentAuditEvent;
import com.heuristiq.documents.document.audit.DocumentAuditStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
public class PostgresDocumentAuditStore implements DocumentAuditStore {

    private final DocumentAuditJpaRepository repository;

    public PostgresDocumentAuditStore(DocumentAuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentAuditEvent save(DocumentAuditEvent event) {
        if (!StringUtils.hasText(event.getId())) {
            event.setId(UUID.randomUUID().toString());
        }
        return repository.save(DocumentAuditJpaEntity.fromDomain(event)).toDomain();
    }
}
