package com.railcab.dms.readiness.application;

import java.util.Set;

/**
 * Fixed values of the DMS readiness report (docs/APPLICATION_READINESS.md): the check id, its message key, the Rider_Web fix
 * route, param names, the reasons given for UNKNOWN and the business applications the check applies to.
 */
public final class DmsReadinessConstants {

    /** Service name in the report. */
    public static final String SERVICE = "dms";

    /** Check id (never renamed once released). */
    public static final String CHECK_DOCUMENT_TYPES_CONFIGURED = "DMS_DOCUMENT_TYPES_CONFIGURED";
    /** Message key base; the status in lower case is appended. */
    public static final String KEY_DOCUMENT_TYPES_CONFIGURED = "readiness.dms.documentTypesConfigured";

    /** Rider_Web route of the document types screen; {@code ?application=<CODE>} is appended. */
    public static final String FIX_PATH_DOCUMENT_TYPES = "/document-management/document-types";
    public static final String APPLICATION_QUERY = "?application=";

    /** Param names. */
    public static final String PARAM_ACTIVE_TYPES = "activeTypes";
    public static final String PARAM_SCOPE = "scope";
    public static final String PARAM_REASON = "reason";

    /** Document types are not linked to an application: the check counts the whole organization. */
    public static final String SCOPE_ORGANIZATION = "ORGANIZATION";

    /** Reasons given in {@link #PARAM_REASON} for UNKNOWN. */
    public static final String REASON_TIMEOUT = "TIMEOUT";
    public static final String REASON_EVALUATION_FAILED = "EVALUATION_FAILED";

    /** One record is enough to know whether any exists; the total comes from the page. */
    public static final int PROBE_PAGE = 0;
    public static final int PROBE_SIZE = 1;

    /**
     * The business applications of Rider_UAM's catalogue. Any other code (a support app or an unknown one) gets
     * NOT_APPLICABLE.
     */
    public static final Set<String> BUSINESS_APPLICATIONS = Set.of(
            "PASUPU_KUMKUMA_MATRIMONY_APP", "BESTRIDE_RIDER_APP", "FOOD_DELIVER_APP", "BEST_PURCHASE_APP",
            "CENTRALISED_WALLET_APP");

    private DmsReadinessConstants() {
    }
}
