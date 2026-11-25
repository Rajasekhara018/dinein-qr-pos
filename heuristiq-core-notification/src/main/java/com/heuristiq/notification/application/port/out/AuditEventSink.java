package com.heuristiq.notification.application.port.out;

import java.util.List;

/** Destination of communication audit events (the central audit service). */
public interface AuditEventSink {

    /**
     * Delivers a batch. The audit service de-duplicates on {@code eventId}, so resending is safe.
     *
     * @param eventsJson one JSON object per event
     * @throws AuditSinkException when the batch was not accepted (service down, circuit open, 5xx)
     */
    void send(List<String> eventsJson);

    /** The audit service did not take the batch; the caller keeps the events and retries later. */
    class AuditSinkException extends RuntimeException {
        public AuditSinkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
