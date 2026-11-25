package com.heuristiq.identity.readiness.domain;

import java.util.Optional;

/**
 * Who reports a readiness check: Rider_UAM itself or one of the services it calls. The slug is the {@code service} value on
 * the wire and the second segment of every message key ({@code readiness.<slug>.<check>.<status>}); the prefix starts every
 * check id the source reports.
 */
public enum ReadinessSource {
    UAM("uam", "UAM_"),
    WALLET("wallet", "WALLET_"),
    ONBOARDING("onboarding", "ONBOARDING_"),
    REPORTS("reports", "REPORTS_"),
    DMS("dms", "DMS_"),
    NMS("nms", "NMS_");

    private final String slug;
    private final String checkPrefix;

    ReadinessSource(String slug, String checkPrefix) {
        this.slug = slug;
        this.checkPrefix = checkPrefix;
    }

    /** The lower-case service name used on the wire and in message keys. */
    public String slug() {
        return slug;
    }

    /** The prefix of every check id this source reports. */
    public String checkPrefix() {
        return checkPrefix;
    }

    /** True for the services Rider_UAM calls over HTTP (everything except UAM itself). */
    public boolean remote() {
        return this != UAM;
    }

    /** The source whose prefix starts the check id, or empty for an id with no known prefix. */
    public static Optional<ReadinessSource> ofCheckId(String checkId) {
        if (checkId == null) {
            return Optional.empty();
        }
        for (ReadinessSource source : values()) {
            if (checkId.startsWith(source.checkPrefix)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
