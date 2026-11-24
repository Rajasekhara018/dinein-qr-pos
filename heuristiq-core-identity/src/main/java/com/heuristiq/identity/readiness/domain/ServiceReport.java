package com.heuristiq.uam.readiness.domain;

import java.util.List;

/**
 * What one service answered for one application and one organization, or why it gave no usable answer.
 *
 * @param source  the service
 * @param outcome whether it answered
 * @param reason  why there is no usable answer (a {@link ReadinessConstants} reason code), null when answered
 * @param checks  the checks it reported; empty unless answered
 */
public record ServiceReport(ReadinessSource source, Outcome outcome, String reason, List<ReadinessCheck> checks) {

    /** How the call to the service ended. */
    public enum Outcome {
        /** The service answered with a readable report. */
        ANSWERED,
        /** The service answered but refused the call (a 4xx: wrong credential, wrong path): a configuration problem. */
        REFUSED,
        /** The service answered with a body that is not a readiness report. */
        MALFORMED,
        /** No answer: timeout, connection error, server error, circuit open, too busy. */
        UNAVAILABLE
    }

    public ServiceReport {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    /** A readable report. */
    public static ServiceReport answered(ReadinessSource source, List<ReadinessCheck> checks) {
        return new ServiceReport(source, Outcome.ANSWERED, null, checks);
    }

    /** The service answered but refused, with the reason (e.g. {@code HTTP_401}). */
    public static ServiceReport refused(ReadinessSource source, String reason) {
        return new ServiceReport(source, Outcome.REFUSED, reason, List.of());
    }

    /** The service answered with something that is not a readiness report. */
    public static ServiceReport malformed(ReadinessSource source) {
        return new ServiceReport(source, Outcome.MALFORMED, ReadinessConstants.REASON_MALFORMED_RESPONSE, List.of());
    }

    /** No answer, with the reason (e.g. {@code TIMEOUT}). */
    public static ServiceReport unavailable(ReadinessSource source, String reason) {
        return new ServiceReport(source, Outcome.UNAVAILABLE, reason, List.of());
    }

    /** True when the service gave a readable report. */
    public boolean answered() {
        return outcome == Outcome.ANSWERED;
    }
}
