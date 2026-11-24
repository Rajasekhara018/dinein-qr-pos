package com.railcab.dms.document.audit;

/**
 * Persistence port for audit events. See {@link com.railcab.dms.document.persistence.DocumentMetadataStore}
 * for why this indirection exists.
 */
public interface DocumentAuditStore {

    DocumentAuditEvent save(DocumentAuditEvent event);
}
