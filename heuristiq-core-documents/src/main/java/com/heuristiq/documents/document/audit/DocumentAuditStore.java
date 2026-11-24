package com.heuristiq.documents.document.audit;

/**
 * Persistence port for audit events. See {@link com.heuristiq.documents.document.persistence.DocumentMetadataStore}
 * for why this indirection exists.
 */
public interface DocumentAuditStore {

    DocumentAuditEvent save(DocumentAuditEvent event);
}
