package com.heuristiq.identity.application.catalogue;

import java.util.List;
import java.util.Locale;

/**
 * The default support apps: always available to every organization, never created from the Applications screen and never
 * part of the {@link ApplicationCatalogue}. Every business application's readiness includes their steps
 * (docs/APPLICATION_READINESS.md).
 */
public final class SupportApplications {

    /** User and access management (Rider_UAM). */
    public static final String UAM = "UAM";

    /** Document management (Rider_DMS). */
    public static final String DMS = "DMS";

    /** Notifications (Rider_NMS). */
    public static final String NMS = "NMS";

    /** Reports (Rider_ReportService). */
    public static final String REPORTS = "REPORTS";

    /** Every support app, in display order. */
    public static final List<String> CODES = List.of(UAM, DMS, NMS, REPORTS);

    private SupportApplications() {
    }

    /** True when the code (trimmed, any case) is a support app. */
    public static boolean contains(String code) {
        return code != null && CODES.contains(code.trim().toUpperCase(Locale.ROOT));
    }
}
