package com.heuristiq.documents.documenttype.application;

/**
 * Port the document-type writer calls after a change is committed, so a cached rule is dropped at once on this
 * instance. Other instances converge when their cache TTL expires.
 */
public interface DocumentTypeRuleInvalidator {

    /** An invalidator that does nothing (no cache in front of the rules). */
    DocumentTypeRuleInvalidator NONE = (tenantId, documentTypeCode) -> { };

    /**
     * Forgets the cached rule of one document type.
     *
     * @param tenantId         the organization that owns the type
     * @param documentTypeCode the type's code (case-insensitive)
     */
    void evict(String tenantId, String documentTypeCode);
}
