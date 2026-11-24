package com.heuristiq.uam.biometric.adapter.out;

/**
 * Fixed values of the biometric stores: SQL of the PostgreSQL tables (migration V43), and the MongoDB collections (same
 * names) with their field and index names.
 */
final class BiometricAdapterConstants {

    // ---- biometric_credentials -------------------------------------------------------------------------------------

    static final String CREDENTIAL_COLUMNS = "credential_id, user_id, tenant_id, application_code, installation_id, public_key, "
            + "key_algorithm, platform, device_name, status, revoked_reason, created_at, last_used_at, expires_at, revoked_at, updated_at";

    /** Parameters: every column of {@link #CREDENTIAL_COLUMNS}, in order. */
    static final String INSERT_CREDENTIAL_SQL = "INSERT INTO biometric_credentials (" + CREDENTIAL_COLUMNS + ") "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** Parameter: credential id. */
    static final String SELECT_CREDENTIAL_BY_ID_SQL = "SELECT " + CREDENTIAL_COLUMNS + " FROM biometric_credentials WHERE credential_id = ?";

    /** Parameters: application code, installation id. */
    static final String SELECT_ACTIVE_BY_INSTALLATION_SQL = "SELECT " + CREDENTIAL_COLUMNS + " FROM biometric_credentials "
            + "WHERE application_code = ? AND installation_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC";

    /** Parameters: application code, installation id. */
    static final String SELECT_LATEST_BY_INSTALLATION_SQL = "SELECT " + CREDENTIAL_COLUMNS + " FROM biometric_credentials "
            + "WHERE application_code = ? AND installation_id = ? ORDER BY created_at DESC LIMIT 1";

    /** Parameter: user id. */
    static final String SELECT_ACTIVE_BY_USER_SQL = "SELECT " + CREDENTIAL_COLUMNS + " FROM biometric_credentials "
            + "WHERE user_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC";

    /** Parameters: new status, reason, revoked at, updated at, credential id. Only an ACTIVE row changes. */
    static final String END_IF_ACTIVE_SQL = "UPDATE biometric_credentials SET status = ?, revoked_reason = ?, revoked_at = ?, updated_at = ? "
            + "WHERE credential_id = ? AND status = 'ACTIVE'";

    /** Parameters: last used at, expires at, updated at, credential id. Only an ACTIVE row changes. */
    static final String TOUCH_SQL = "UPDATE biometric_credentials SET last_used_at = ?, expires_at = ?, updated_at = ? "
            + "WHERE credential_id = ? AND status = 'ACTIVE'";

    // ---- biometric_challenges --------------------------------------------------------------------------------------

    /** Parameters: challenge id, installation id, challenge, created at, expires at. */
    static final String INSERT_CHALLENGE_SQL = "INSERT INTO biometric_challenges (challenge_id, installation_id, challenge, created_at, expires_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    /** Removes challenges that ended before the parameter (they can never be answered). */
    static final String DELETE_EXPIRED_CHALLENGES_SQL = "DELETE FROM biometric_challenges WHERE expires_at < ?";

    /** Parameters: used at, challenge id, now. Marks it used only while unused and unexpired, and returns it. */
    static final String CONSUME_CHALLENGE_SQL = "UPDATE biometric_challenges SET used_at = ? "
            + "WHERE challenge_id = ? AND used_at IS NULL AND expires_at > ? "
            + "RETURNING challenge_id, installation_id, challenge, created_at, expires_at";

    // ---- biometric_signin_attempts ---------------------------------------------------------------------------------

    /** Parameters: installation id, now. */
    static final String SELECT_FAILURES_SQL = "SELECT failures FROM biometric_signin_attempts WHERE installation_id = ? AND expires_at > ?";

    /**
     * Parameters: installation id, window start (now), window end. Counts one failure in the open window, or opens a new
     * window when the stored one has ended; atomic per row.
     */
    static final String RECORD_FAILURE_SQL = "INSERT INTO biometric_signin_attempts AS a (installation_id, failures, window_started_at, expires_at) "
            + "VALUES (?, 1, ?, ?) ON CONFLICT (installation_id) DO UPDATE SET "
            + "failures = CASE WHEN a.expires_at <= EXCLUDED.window_started_at THEN 1 ELSE a.failures + 1 END, "
            + "window_started_at = CASE WHEN a.expires_at <= EXCLUDED.window_started_at THEN EXCLUDED.window_started_at ELSE a.window_started_at END, "
            + "expires_at = CASE WHEN a.expires_at <= EXCLUDED.window_started_at THEN EXCLUDED.expires_at ELSE a.expires_at END "
            + "RETURNING failures";

    /** Parameter: installation id. */
    static final String DELETE_ATTEMPTS_SQL = "DELETE FROM biometric_signin_attempts WHERE installation_id = ?";

    /** Removes ended windows; parameter: now. */
    static final String DELETE_EXPIRED_ATTEMPTS_SQL = "DELETE FROM biometric_signin_attempts WHERE expires_at < ?";

    // ---- MongoDB ---------------------------------------------------------------------------------------------------

    static final String CREDENTIALS_COLLECTION = "biometric_credentials";
    static final String CHALLENGES_COLLECTION = "biometric_challenges";
    static final String ATTEMPTS_COLLECTION = "biometric_signin_attempts";

    static final String FIELD_ID = "_id";
    static final String FIELD_USER_ID = "userId";
    static final String FIELD_TENANT_ID = "tenantId";
    static final String FIELD_APPLICATION_CODE = "applicationCode";
    static final String FIELD_INSTALLATION_ID = "installationId";
    static final String FIELD_PUBLIC_KEY = "publicKey";
    static final String FIELD_KEY_ALGORITHM = "keyAlgorithm";
    static final String FIELD_PLATFORM = "platform";
    static final String FIELD_DEVICE_NAME = "deviceName";
    static final String FIELD_STATUS = "status";
    static final String FIELD_REVOKED_REASON = "revokedReason";
    static final String FIELD_CREATED_AT = "createdAt";
    static final String FIELD_LAST_USED_AT = "lastUsedAt";
    static final String FIELD_EXPIRES_AT = "expiresAt";
    static final String FIELD_REVOKED_AT = "revokedAt";
    static final String FIELD_UPDATED_AT = "updatedAt";
    static final String FIELD_CHALLENGE = "challenge";
    static final String FIELD_USED_AT = "usedAt";
    static final String FIELD_FAILURES = "failures";
    static final String FIELD_WINDOW_STARTED_AT = "windowStartedAt";

    static final String STATUS_ACTIVE = "ACTIVE";

    static final String INDEX_USER_APP_INSTALLATION = "idx_biometric_credentials_user_app_installation";
    static final String INDEX_INSTALLATION = "idx_biometric_credentials_installation";
    static final String INDEX_STATUS = "idx_biometric_credentials_status";
    static final String INDEX_ACTIVE_INSTALLATION = "uk_biometric_credentials_active_installation";
    static final String INDEX_CHALLENGES_TTL = "ttl_biometric_challenges_expires";
    static final String INDEX_ATTEMPTS_TTL = "ttl_biometric_signin_attempts_expires";

    /** Expired challenges are kept this long before the PostgreSQL clean-up removes them (seconds). */
    static final long CHALLENGE_RETENTION_SECONDS = 3600;

    /** A Mongo document is removed by the TTL monitor once its {@code expiresAt} has passed. */
    static final long TTL_AT_EXPIRY_SECONDS = 0;

    private BiometricAdapterConstants() {
    }
}
