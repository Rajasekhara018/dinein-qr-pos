package com.heuristiq.documents.document.domain;

import java.util.List;

/** Fixed values of document expiry tracking (D12). The reminder days and the time zone are settings, not constants. */
public final class DocumentValidityConstants {

    /**
     * The driver documents whose expiry is tracked: the categories the captain app uploads through Dispatch
     * ({@code "driver-" + DriverDocumentType}). A lapsed one blocks the driver from going online.
     */
    public static final List<String> TRACKED_CATEGORIES =
            List.of("driver-license", "driver-rc", "driver-insurance", "driver-puc", "driver-permit", "driver-fitness");

    /** {@link DocumentMetadata#getExpiryNoticeDays()} once the "your document has lapsed" notice was sent. */
    public static final int LAPSED_NOTICE = -1;

    /** How many days after lapsing the lapsed notice may still be sent (so turning the job on does not message ancient lapses). */
    public static final int LAPSED_NOTICE_WINDOW_DAYS = 7;

    private DocumentValidityConstants() {
    }
}
