package com.heuristiq.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables of the audit service (prefix {@code railcab.audit}); see {@code application.properties}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "railcab.audit")
public class AuditProperties {

    private final Ingest ingest = new Ingest();
    private final Query query = new Query();
    private final Retention retention = new Retention();
    private final Payload payload = new Payload();

    /** Limits applied when events are accepted. */
    @Getter
    @Setter
    public static class Ingest {
        /** Largest batch accepted on the internal endpoint. */
        private int maxBatchSize = 500;
        /** Largest batch accepted on the screen-view endpoint. */
        private int screenViewMaxBatchSize = 50;
        /** Screen-view events one user may send per minute on this instance. */
        private int screenViewsPerMinute = 60;
        /** Mask the last IPv4 octet / the host half of an IPv6 address. */
        private boolean maskIp = true;
        /** Events dated further in the future than this are clamped to the receive time. */
        private long maxFutureSkewSeconds = 300;
    }

    /** Limits of the read side. */
    @Getter
    @Setter
    public static class Query {
        private int maxPageSize = 200;
        private int exportMaxRows = 10_000;
        private int exportPayloadMaxRows = 1_000;
        private int facetWindowDays = 30;
        private int facetMaxValues = 500;
    }

    /** Days an event is kept, per retention class, plus the purge job switches. CERT-In requires at least 180 days for every class. */
    @Getter
    @Setter
    public static class Retention {
        private int accessReadDays = 180;
        private int screenViewDays = 180;
        private int accessWriteDays = 180;
        private int communicationDays = 365;
        private int paymentGatewayDays = 365;
        private int domainDays = 365;
        private boolean purgeEnabled = true;
        private long purgeIntervalMs = 3_600_000L;
    }

    /** Header and body storage limits and the paths whose bodies are never stored. */
    @Getter
    @Setter
    public static class Payload {
        /** Longest body kept per direction (characters); longer ones are cut and flagged truncated. */
        private int maxBodyBytes = 16_384;
        private int maxHeaders = 60;
        /** Ant patterns on the event path; bodies of matching events are dropped (login, OTP, password, token, card). */
        private java.util.List<String> skipBodyPaths = new java.util.ArrayList<>(
                com.heuristiq.audit.application.RedactionConstants.DEFAULT_SKIP_BODY_PATHS);
    }
}
