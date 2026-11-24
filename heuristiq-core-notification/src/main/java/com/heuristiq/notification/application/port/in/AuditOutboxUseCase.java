package com.railcab.nms.application.port.in;

/** Pushes queued communication audit events to the central audit service. */
public interface AuditOutboxUseCase {

    /**
     * Sends one batch of due events. Never throws: a failure schedules a retry.
     *
     * @return number of events the audit service accepted in this call
     */
    int drain();
}
