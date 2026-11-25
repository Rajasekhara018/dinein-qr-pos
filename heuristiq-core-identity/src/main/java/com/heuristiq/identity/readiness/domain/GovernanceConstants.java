package com.heuristiq.identity.readiness.domain;

/** Fixed values of the go-live guard, override, owner team and readiness alerts. */
public final class GovernanceConstants {

    /** Machine-readable error code of the 409 returned when go-live is refused. */
    public static final String ERROR_GO_LIVE_BLOCKED = "GO_LIVE_BLOCKED";

    /** English fallback text of that 409 (Rider_Web translates {@link #KEY_GO_LIVE_REFUSED}). */
    public static final String GO_LIVE_REFUSED_MESSAGE = "This application cannot go live while required setup steps are failing or unknown";

    /** Message keys Rider_Web translates (guard, override, owner team). */
    public static final String KEY_GO_LIVE_REFUSED = "readiness.governance.goLiveRefused";
    public static final String KEY_OVERRIDE_FORBIDDEN = "readiness.governance.overrideForbidden";
    public static final String KEY_OVERRIDE_REASON_INVALID = "readiness.governance.overrideReasonInvalid";
    public static final String KEY_NOTHING_TO_OVERRIDE = "readiness.governance.nothingToOverride";
    public static final String KEY_OWNER_NOT_ELIGIBLE = "readiness.governance.ownerNotEligible";
    public static final String KEY_OWNER_TEAM_TOO_LARGE = "readiness.governance.ownerTeamTooLarge";

    /** English fallback texts for the keys above. */
    public static final String OVERRIDE_FORBIDDEN_MESSAGE = "Only a platform administrator (SUPER_ADMIN) may override the go-live guard";
    public static final String OVERRIDE_REASON_INVALID_MESSAGE = "The override reason must be between %d and %d characters";
    public static final String NOTHING_TO_OVERRIDE_MESSAGE = "No required step is blocking this application; switch it on normally";
    public static final String OWNER_NOT_ELIGIBLE_MESSAGE = "Owners must be department heads of the organization: ";
    public static final String OWNER_TEAM_TOO_LARGE_MESSAGE = "An owner team may have at most %d members";

    /** NMS event codes (English base). A language variant appends {@code _<LANG>} (see {@link #languageEvent}). */
    public static final String EVENT_READINESS_BLOCKED = "APPLICATION_READINESS_BLOCKED";
    public static final String EVENT_GO_LIVE_REFUSED = "APPLICATION_GO_LIVE_REFUSED";
    public static final String EVENT_GO_LIVE_OVERRIDDEN = "APPLICATION_GO_LIVE_OVERRIDDEN";

    /** NMS template variables; every language variant of an event uses exactly the same set. */
    public static final String VAR_RECIPIENT_NAME = "recipientName";
    public static final String VAR_APPLICATION_NAME = "applicationName";
    public static final String VAR_BLOCKING_STEPS = "blockingSteps";
    public static final String VAR_ATTEMPTED_BY = "attemptedBy";
    public static final String VAR_OVERRIDDEN_BY = "overriddenBy";
    public static final String VAR_REASON = "reason";

    /** Entity type sent to NMS as part of the origin. */
    public static final String ORIGIN_ENTITY_TYPE = "TENANT_APPLICATION";

    /** Audit entity type of the domain audit events. */
    public static final String AUDIT_ENTITY_TYPE = "TENANT_APPLICATION";
    public static final String AUDIT_REMARK_OVERRIDE = "Go-live guard overridden by SUPER_ADMIN";
    public static final String AUDIT_REMARK_REFUSED = "Go-live refused by the readiness guard";

    /** Fallback language of an alert (English templates have no suffix). */
    public static final String DEFAULT_LANGUAGE = "en";

    private GovernanceConstants() {
    }

    /** The NMS event code for a language: the base code for English or an unknown language, else base + {@code _<LANG>}. */
    public static String languageEvent(String baseEvent, String language) {
        if (language == null || language.isBlank() || DEFAULT_LANGUAGE.equalsIgnoreCase(language)) {
            return baseEvent;
        }
        return baseEvent + "_" + language.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
