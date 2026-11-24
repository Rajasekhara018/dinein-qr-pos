package com.railcab.dms.common;

/**
 * Fixed values shared by more than one DMS feature: wire values (header names, the bearer prefix, public paths), the
 * MDC key of the correlation id, the persistence-provider switch and the digest algorithm name. Anything used by a
 * single feature lives in that feature's own constants class.
 */
public final class DmsCommonConstants {

    private DmsCommonConstants() {
    }

    // ---- HTTP wire values
    /** Header carrying the shared service-to-service token (inbound internal calls, outbound audit calls). */
    public static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";
    /** Header carrying the correlation id of a request. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    /** Prefix of an {@code Authorization} header holding a bearer token. */
    public static final String BEARER_PREFIX = "Bearer ";
    /** Regex matching the trailing slashes of a configured base URL (stripped before a path is appended). */
    public static final String TRAILING_SLASHES_REGEX = "/+$";

    /** Base path of the document endpoints. */
    public static final String DOCUMENTS_BASE_PATH = "/api/v1/dms/documents";
    /** Path prefix of the signed-token (public) download endpoint; the token follows it. */
    public static final String PUBLIC_DOWNLOAD_PATH = DOCUMENTS_BASE_PATH + "/public/";

    // ---- logging
    /** MDC key holding the correlation id of the current request. */
    public static final String MDC_KEY = "correlationId";

    // ---- persistence provider switch (used in {@code @ConditionalOnProperty})
    public static final String PERSISTENCE_PROPERTY_PREFIX = "railcab.dms.persistence";
    public static final String PERSISTENCE_PROVIDER_PROPERTY = "provider";
    public static final String PERSISTENCE_MONGO = "mongo";
    public static final String PERSISTENCE_POSTGRES = "postgres";

    // ---- digests
    /** {@link java.security.MessageDigest} algorithm used for checksums and change-request hashes. */
    public static final String SHA_256 = "SHA-256";
}
