package com.railcab.nms.application.usecase.support;

/** Algorithm names and signature formats used to verify provider callbacks. */
final class CallbackSecurityConstants {

    static final String HMAC_SHA1 = "HmacSHA1";
    static final String HMAC_SHA256 = "HmacSHA256";
    /** Prefix of Meta's {@code X-Hub-Signature-256} header value. */
    static final String META_SIGNATURE_PREFIX = "sha256=";
    /** Scheme prefix of an HTTP basic {@code Authorization} header (compared case-insensitively). */
    static final String BASIC_AUTH_PREFIX = "Basic ";
    /** Separator of user name and password inside decoded basic credentials. */
    static final char BASIC_AUTH_SEPARATOR = ':';

    private CallbackSecurityConstants() {
    }
}
