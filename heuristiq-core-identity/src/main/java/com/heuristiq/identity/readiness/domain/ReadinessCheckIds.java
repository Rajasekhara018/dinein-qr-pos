package com.heuristiq.identity.readiness.domain;

import java.util.Set;

/**
 * Every check id of the first release (docs/APPLICATION_READINESS.md, "Check ids per service"), plus the synthetic steps
 * Rider_UAM adds itself (one {@code <SERVICE>_REACHABLE} per service and {@code GO_LIVE_REVIEW}). Ids are never renamed once
 * released: the profile, alerts and Rider_Web's message keys refer to them.
 */
public final class ReadinessCheckIds {

    // UAM (computed by the combiner from UAM data)
    public static final String UAM_APPLICATION_ACTIVE = "UAM_APPLICATION_ACTIVE";
    public static final String UAM_APPLICATION_IN_ORGANIZATION = "UAM_APPLICATION_IN_ORGANIZATION";

    // Wallet
    public static final String WALLET_APP_REGISTERED = "WALLET_APP_REGISTERED";
    public static final String WALLET_APP_ENABLED = "WALLET_APP_ENABLED";
    public static final String WALLET_APP_CAN_ISSUE = "WALLET_APP_CAN_ISSUE";
    public static final String WALLET_APP_CAN_CONSUME = "WALLET_APP_CAN_CONSUME";
    public static final String WALLET_TREASURY_CONFIGURED = "WALLET_TREASURY_CONFIGURED";
    public static final String WALLET_SYSTEM_ACCOUNTS_READY = "WALLET_SYSTEM_ACCOUNTS_READY";

    // Reports
    public static final String REPORTS_SYSTEM_CONFIGURATION_EXISTS = "REPORTS_SYSTEM_CONFIGURATION_EXISTS";
    public static final String REPORTS_CONNECTION_TESTED = "REPORTS_CONNECTION_TESTED";
    public static final String REPORTS_FORMATS_CONFIGURED = "REPORTS_FORMATS_CONFIGURED";
    public static final String REPORTS_CUSTOM_FIELDS_EXIST = "REPORTS_CUSTOM_FIELDS_EXIST";
    public static final String REPORTS_TEMPLATE_EXISTS = "REPORTS_TEMPLATE_EXISTS";
    public static final String REPORTS_CONFIGURATION_EXISTS = "REPORTS_CONFIGURATION_EXISTS";

    // Onboarding
    public static final String ONBOARDING_TYPE_ACTIVE = "ONBOARDING_TYPE_ACTIVE";
    public static final String ONBOARDING_SCHEMA_PUBLISHED = "ONBOARDING_SCHEMA_PUBLISHED";
    public static final String ONBOARDING_REVIEW_PROCESS_AVAILABLE = "ONBOARDING_REVIEW_PROCESS_AVAILABLE";

    // DMS and NMS
    public static final String DMS_DOCUMENT_TYPES_CONFIGURED = "DMS_DOCUMENT_TYPES_CONFIGURED";
    public static final String NMS_TEMPLATES_EXIST = "NMS_TEMPLATES_EXIST";

    /** Suffix of the synthetic connectivity step of each service: {@code <SERVICE PREFIX>REACHABLE}. */
    public static final String REACHABLE_SUFFIX = "REACHABLE";

    /** The synthetic final step: passes only when every blocking step passes. */
    public static final String GO_LIVE_REVIEW = "GO_LIVE_REVIEW";

    /** The check ids of the contract (the ones a service or UAM reports), first release. */
    public static final Set<String> CONTRACT_IDS = Set.of(
            UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION,
            WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, WALLET_APP_CAN_CONSUME,
            WALLET_TREASURY_CONFIGURED, WALLET_SYSTEM_ACCOUNTS_READY,
            REPORTS_SYSTEM_CONFIGURATION_EXISTS, REPORTS_CONNECTION_TESTED, REPORTS_FORMATS_CONFIGURED,
            REPORTS_CUSTOM_FIELDS_EXIST, REPORTS_TEMPLATE_EXISTS, REPORTS_CONFIGURATION_EXISTS,
            ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED, ONBOARDING_REVIEW_PROCESS_AVAILABLE,
            DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST);

    private ReadinessCheckIds() {
    }

    /** The connectivity step id of a service, e.g. {@code WALLET_REACHABLE}. */
    public static String reachable(ReadinessSource source) {
        return source.checkPrefix() + REACHABLE_SUFFIX;
    }
}
