package com.heuristiq.documents.document.application;

/** Paths, header and messages of the internal download-link endpoint. */
public final class InternalLinkConstants {

    private InternalLinkConstants() {
    }

    public static final String LINK_PATH = "/api/v1/dms/internal/documents/{id}/link";
    /** The organization the calling service acts for (required; never defaulted). */
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_REQUIRED = "X-Tenant-Id is required";
    public static final String NOT_FOUND = "Document metadata not found";
    public static final String AUDIT_MESSAGE = "Download link issued to a platform service";
}
