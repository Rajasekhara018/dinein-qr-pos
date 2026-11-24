package com.heuristiq.documents.expiry.adapter.in;

/** Paths of document expiry (D12). */
public final class ExpiryWebConstants {

    /** Internal (service credential only): which drivers have a lapsed document. */
    public static final String LAPSED_PATH = "/api/v1/dms/internal/document-validity/lapsed";

    private ExpiryWebConstants() {
    }
}
