package com.railcab.dms.config;

/** Fixed security values of DMS configuration. */
public final class DmsSecurityDefaults {

    private DmsSecurityDefaults() {
    }

    /** The placeholder download-token secret shipped for development. The production profile refuses to start with it. */
    public static final String DEFAULT_DOWNLOAD_TOKEN_SECRET = "railcab-dms-download-token-secret-change-in-production";

    /** Shortest download-token secret the production profile accepts. */
    public static final int MIN_DOWNLOAD_TOKEN_SECRET_LENGTH = 32;

    /** Spring profile in which the secret guard is active. */
    public static final String PRODUCTION_PROFILE = "prod";
}
