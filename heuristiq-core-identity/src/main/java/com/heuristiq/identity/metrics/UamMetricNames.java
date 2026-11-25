package com.heuristiq.identity.metrics;

import java.time.Duration;

/**
 * Names, tag keys and histogram bounds of the UAM business metrics published by {@link UamMetrics}.
 *
 * <p>Convention (see {@code ops/observability/README.md}): dotted lowercase {@code <service>.<area>.<name>};
 * Prometheus turns dots into underscores, adds {@code _total} to counters and {@code _seconds} to timers.
 * Tags must stay low-cardinality: closed sets only, never user ids, e-mails, phones, IPs or paths.
 *
 * <p>The older metrics ({@code uam.auth.login.attempts}, {@code uam.auth.lockouts},
 * {@code uam.auth.lockout.active}, {@code uam.auth.lockout.admin_unlock}, {@code uam.auth.audit.events},
 * {@code uam.auth.audit.persist.errors}, {@code uam_jwt_validation_failures_total}) are recorded where they
 * always were and keep their names, because dashboards and alerts depend on them.
 */
public final class UamMetricNames {

    /** Timer: wall time of a password login, tagged {@link #TAG_RESULT}. */
    public static final String LOGIN_DURATION = "uam.auth.login.duration";
    /** Counter: token refresh attempts, tagged {@link #TAG_RESULT}. */
    public static final String REFRESH_ATTEMPTS = "uam.auth.refresh.attempts";
    /** Timer: wall time of a token refresh, tagged {@link #TAG_RESULT}. */
    public static final String REFRESH_DURATION = "uam.auth.refresh.duration";
    /** Counter: OTP sends, tagged {@link #TAG_CHANNEL} and {@link #TAG_RESULT}. */
    public static final String OTP_SEND = "uam.otp.send";
    /** Counter: OTP verifications (sign-in), tagged {@link #TAG_CHANNEL} and {@link #TAG_RESULT}. */
    public static final String OTP_VERIFY = "uam.otp.verify";
    /** Counter: user creations through the user service, tagged {@link #TAG_RESULT}. */
    public static final String USER_CREATED = "uam.users.created";
    /** Gauge: state of an outbound circuit breaker, tagged {@link #TAG_NAME}; 0 closed, 1 half open, 2 open. */
    public static final String DEPENDENCY_CIRCUIT_STATE = "uam.dependency.circuit.state";

    /** Counter (legacy name, kept for dashboards): rejected bearer tokens, tagged {@link #TAG_REASON}. */
    public static final String JWT_VALIDATION_FAILURES = "uam_jwt_validation_failures_total";
    /** {@link #TAG_REASON} values of {@link #JWT_VALIDATION_FAILURES}. */
    public static final String JWT_FAILURE_EXPIRED = "expired";
    public static final String JWT_FAILURE_MALFORMED = "malformed";
    public static final String JWT_FAILURE_INVALID_SIGNATURE = "invalid_signature";
    public static final String JWT_FAILURE_OTHER = "other";

    public static final String TAG_REASON = "reason";
    public static final String TAG_RESULT = "result";
    public static final String TAG_CHANNEL = "channel";
    public static final String TAG_NAME = "name";

    /** Histogram buckets for login latency: bcrypt makes the typical login a few hundred milliseconds. */
    public static final Duration[] LOGIN_SLO = {
        Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500),
        Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5)
    };
    /** Histogram buckets for refresh latency (database bound, no hashing). */
    public static final Duration[] REFRESH_SLO = {
        Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
        Duration.ofMillis(500), Duration.ofSeconds(1)
    };

    public static final double CIRCUIT_CLOSED = 0;
    public static final double CIRCUIT_HALF_OPEN = 1;
    public static final double CIRCUIT_OPEN = 2;

    private UamMetricNames() {
    }
}
