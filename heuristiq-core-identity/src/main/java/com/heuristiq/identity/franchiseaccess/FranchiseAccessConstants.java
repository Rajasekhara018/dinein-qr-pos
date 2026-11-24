package com.heuristiq.uam.franchiseaccess;

/** Fixed values of the franchise-access internal API. */
final class FranchiseAccessConstants {

    /** Base path; under the service-token-only internal chain (see InternalServiceSecurityConfig). */
    static final String BASE_PATH = "/api/v1/uam/internal/franchise-access";
    /** Note written on an application grant when the caller gives none. */
    static final String DEFAULT_GRANT_NOTE = "Franchise";
    /** Actor recorded when the caller names none. */
    static final String DEFAULT_ACTOR = "franchise-service";

    /** Prefix of {@link FranchiseAccessProperties}. */
    static final String PROPERTIES_PREFIX = "railcab.uam.franchise-access";
    /** Role codes never handed out through this API (organization administrator roles end in the suffix below and are denied too). */
    static final java.util.List<String> DEFAULT_DENIED_ROLES = java.util.List.of("SUPER_ADMIN", "ADMIN");
    static final String ADMIN_ROLE_SUFFIX = "_ADMIN";
    /**
     * The privileges (exact names in privilege.json) a franchise role may carry: exactly the union of the franchise owner set and the
     * franchise agent set, no wildcard. Nothing that sets a money rule, approves a payout or views every franchise's ledger.
     */
    static final java.util.List<String> DEFAULT_ALLOWED_PRIVILEGE_PREFIXES = java.util.List.of("FRANCHISE_MINE_LIST",
            "FRANCHISE_AGENT_LIST", "FRANCHISE_AGENT_CREATE", "FRANCHISE_AGENT_REQUEST_CREATE", "FRANCHISE_MEMBER_LIST",
            "LEDGER_ENTRY_MINE_LIST", "COMMISSION_STATEMENT_MINE_READ", "PAYOUT_REQUEST_CREATE", "PAYOUT_REQUEST_MINE_LIST",
            "WALLET_SELF_STATEMENT_READ", "WALLET_SELF_STATEMENT_MONTH_LIST", "WALLET_SELF_STATEMENT_SUMMARY_READ",
            "WALLET_SELF_STATEMENT_EXPORT_READ", "FRANCHISE_AGENT_DEAL_UPDATE", "FRANCHISE_AGENT_DEAL_READ", "FRANCHISE_AGENT_DEAL_MINE_READ",
            "NMS_IN_APP_NOTIFICATION_LIST", "NMS_IN_APP_NOTIFICATION_UNREAD_COUNT", "NMS_IN_APP_NOTIFICATION_MARK_READ", "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ", "NMS_IN_APP_WS_CONNECT",
            "FRANCHISE_DRIVER_LIST", "FRANCHISE_DRIVER_ONBOARDING_CREATE", "FRANCHISE_DRIVER_SUBSCRIPTION_OFFER_LIST", "FRANCHISE_DRIVER_SUBSCRIPTION_CREATE",
            "USER_SELF_PROFILE_READ", "USER_SELF_PREFERENCE_READ", "USER_SELF_SESSION_LIST", "USER_SELF_DEVICE_LIST", "USER_SELF_MFA_LIST", "USER_SELF_LOGIN_SECURITY_READ", "WORKFLOW_TASK_LIST",
            // the owner's accounting of a franchise they own, answered by Rider_WalletService for an active OWNER member only
            "WALLET_FRANCHISE_ACCOUNTING_MINE_READ",
            // the matrimony agent desk (owner and agent roles of a matrimony application) and franchise management (owner role only)
            "MATRIMONY_AGENT_PROFILE_READ", "MATRIMONY_AGENT_DASHBOARD_READ", "MATRIMONY_AGENT_ONBOARDING_FEE_READ",
            "MATRIMONY_AGENT_REFERRAL_LIST", "MATRIMONY_AGENT_CUSTOMER_CREATE", "MATRIMONY_AGENT_CUSTOMER_FEE_CREATE",
            "MATRIMONY_AGENT_CUSTOMER_CONSENT_OTP_CREATE", "MATRIMONY_AGENT_CUSTOMER_CONSENT_OTP_VERIFY_CREATE", "MATRIMONY_AGENT_CUSTOMER_CONSENT_SIGNED_FORM_CREATE",
            "MATRIMONY_AGENT_CUSTOMER_CONSENT_LIST", "MATRIMONY_AGENT_CUSTOMER_CONSENT_DELETE", "MATRIMONY_AGENT_CUSTOMER_PHOTO_CREATE", "MATRIMONY_AGENT_CUSTOMER_PHOTO_LIST", "MATRIMONY_AGENT_CUSTOMER_PHOTO_READ", "MATRIMONY_AGENT_CUSTOMER_PHOTO_DELETE", "MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_READ",
            "MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_CREATE", "MATRIMONY_AGENT_CUSTOMER_PREFERENCE_UPDATE", "MATRIMONY_AGENT_CUSTOMER_ACTIVATE", "MATRIMONY_AGENT_CUSTOMER_PENDING_ACTIVATION_LIST", "MATRIMONY_FRANCHISE_BIODATA_READ",
            "MATRIMONY_FRANCHISE_BIODATA_UPDATE", "MATRIMONY_FRANCHISE_CONSENT_CREATE", "MATRIMONY_FRANCHISE_CUSTOMER_LOOKUP_LIST",
            "MATRIMONY_FRANCHISE_PROPOSAL_CREATE", "MATRIMONY_FRANCHISE_PROPOSAL_LIST", "MATRIMONY_FRANCHISE_PROPOSAL_READ", "MATRIMONY_FRANCHISE_PROPOSAL_PHOTO_READ",
            "MATRIMONY_FRANCHISE_PROPOSAL_STEP_CREATE", "MATRIMONY_FRANCHISE_PROPOSAL_CONFIRMATION_CREATE", "MATRIMONY_FRANCHISE_PROPOSAL_SUCCESS_STORY_CREATE",
            "MATRIMONY_FRANCHISE_FUNNEL_READ", "MATRIMONY_FRANCHISE_COMPLAINT_LIST", "MATRIMONY_FRANCHISE_COMPLAINT_UPDATE",
            "MATRIMONY_FRANCHISE_AGENT_RATING_LIST", "MATRIMONY_AGENT_RATINGS_DIRECTORY_LIST", "MATRIMONY_FRANCHISE_CUSTOMER_AGENT_UPDATE",
            "MATRIMONY_FRANCHISE_AGENT_CUSTOMER_REASSIGN_CREATE", "MATRIMONY_REASSIGNMENT_AGENT_LIST", "MATRIMONY_REASSIGNMENT_CUSTOMER_LIST");
    /**
     * Base codes of the fixed franchise roles: a role is one of them when its code is a base or a base followed by the separator and an
     * application code (for example FRANCHISE_OWNER_MATRIMONY). Only these roles are ever extended through the API.
     */
    static final java.util.List<String> DEFAULT_FIXED_ROLE_BASES = java.util.List.of("FRANCHISE_OWNER", "FRANCHISE_AGENT");
    /**
     * Franchise privileges that were removed from privilege.json (ids 1065-1068, the agent-role links). An older franchise service
     * may still send them during a rolling deploy; they are ignored with a warning instead of failing the call.
     */
    static final java.util.Set<String> RETIRED_PRIVILEGES = java.util.Set.of("FRANCHISE_AGENT_ROLE_MINE_LIST",
            "FRANCHISE_AGENT_ROLE_LIST", "FRANCHISE_AGENT_ROLE_CREATE", "FRANCHISE_AGENT_ROLE_DELETE");
    /** Separator between a fixed role's base code and its application code. */
    static final String ROLE_CODE_SEPARATOR = "_";

    private FranchiseAccessConstants() {
    }
}
