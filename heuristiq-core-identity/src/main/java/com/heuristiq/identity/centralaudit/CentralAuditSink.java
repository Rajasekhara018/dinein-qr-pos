package com.heuristiq.uam.centralaudit;

import java.util.List;

/** Port: delivers a batch of events to the central audit service. */
public interface CentralAuditSink {

    /**
     * Sends the batch.
     *
     * @param events events to deliver, never empty
     * @throws RuntimeException when the audit service did not accept the batch (the caller keeps the events for a retry)
     */
    void send(List<CentralAuditEvent> events);
}
