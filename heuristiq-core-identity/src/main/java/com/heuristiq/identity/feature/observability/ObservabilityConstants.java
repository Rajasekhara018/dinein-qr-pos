package com.heuristiq.uam.feature.observability;

import java.util.Map;

/** Fixed values of the UAM persistent-state health check, shared by its Mongo and Postgres variants. */
final class ObservabilityConstants {

    /** Bean (and health component) name; both variants use it so the actuator output is the same. */
    static final String PERSISTENT_STATE_BEAN = "uamPersistentState";

    /** Health detail key to the collection (Mongo) or table (Postgres) that must exist. */
    static final Map<String, String> PERSISTENT_STATE_STORES = Map.of(
            "otpChallenges", "uam_otp_challenges",
            "loginAttempts", "uam_login_attempts",
            "authAuditEvents", "uam_auth_audit_events");

    /** Health detail values. */
    static final String STORE_AVAILABLE = "available";
    static final String STORE_PENDING = "pending";

    private ObservabilityConstants() {
    }
}
