package com.heuristiq.resilient;

/**
 * The downstream could not give an answer: timed out, kept failing, circuit open, or too many calls
 * in flight. Distinct from a 4xx (a real answer, passed through untouched). Callers must map it to a
 * 503 or an explicit degraded path; never to an empty result that looks like real data.
 */
public class DownstreamUnavailableException extends RuntimeException {

    public enum Reason { TIMEOUT, FAILED, CIRCUIT_OPEN, OVERLOADED }

    private final String downstream;
    private final Reason reason;

    public DownstreamUnavailableException(String downstream, Reason reason, Throwable cause) {
        super(downstream + " unavailable (" + reason + ")", cause);
        this.downstream = downstream;
        this.reason = reason;
    }

    public String downstream() { return downstream; }

    public Reason reason() { return reason; }
}
