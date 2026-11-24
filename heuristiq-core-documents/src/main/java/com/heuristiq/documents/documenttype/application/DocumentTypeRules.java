package com.railcab.dms.documenttype.application;

import com.railcab.dms.documenttype.domain.DocumentTypeRule;

/**
 * The published application interface other features (uploads) use to ask which rule a document type imposes.
 */
public interface DocumentTypeRules {

    /**
     * The effective rule of an active document type of the organization.
     *
     * @param tenantId         the organization (from the session)
     * @param category         the upload category; when not null it must equal the type's category
     * @param documentTypeCode the type's code (case-insensitive)
     * @return the rule
     * @throws com.railcab.dms.documenttype.domain.DocumentTypeException (INVALID) when the type is unknown in the
     *         organization, inactive, or belongs to another category
     */
    DocumentTypeRule resolveRule(String tenantId, String category, String documentTypeCode);
}
