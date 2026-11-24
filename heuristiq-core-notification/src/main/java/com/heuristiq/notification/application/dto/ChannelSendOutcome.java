package com.heuristiq.notification.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of one resilience-wrapped {@code provider.send(...)} call, including retry attempts made.
 *
 * @param success        true when some attempt was accepted by the provider
 * @param error          reason of the last failure, null on success
 * @param attempts       provider calls made (retries included)
 * @param attemptResults one entry per provider call, plus one for a call refused by an open circuit
 *                       or a full bulkhead; empty only for callers that do not collect them
 */
public record ChannelSendOutcome(boolean success, String error, int attempts, List<AttemptResult> attemptResults) {

    public ChannelSendOutcome {
        attemptResults = attemptResults == null ? List.of() : List.copyOf(attemptResults);
    }

    /** Back-compatible constructor for callers that do not collect per-attempt detail. */
    public ChannelSendOutcome(boolean success, String error, int attempts) {
        this(success, error, attempts, List.of());
    }

    /**
     * One provider call.
     *
     * @param attemptNumber     1-based
     * @param success           provider accepted it
     * @param providerName      concrete provider, may be null
     * @param providerMessageId provider's id for the message, may be null
     * @param providerStatus    raw provider status, may be null
     * @param errorCode         short code on failure
     * @param errorMessage      sanitised reason on failure
     * @param startedAt         when the call started
     * @param latencyMs         how long the call took; 0 when it was refused before reaching the provider
     */
    public record AttemptResult(int attemptNumber, boolean success, String providerName, String providerMessageId,
            String providerStatus, String errorCode, String errorMessage, LocalDateTime startedAt, long latencyMs) {
    }
}
