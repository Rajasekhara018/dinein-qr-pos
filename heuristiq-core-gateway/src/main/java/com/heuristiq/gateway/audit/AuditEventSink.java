package com.heuristiq.gateway.audit;

import reactor.core.publisher.Mono;

import java.util.List;

/** Where batches of events go. The production implementation posts to Rider_AuditService. */
public interface AuditEventSink {

    /**
     * Sends one batch. An error means the batch is dropped; the caller never retries, so a slow or dead audit
     * service can not pile work up in the gateway.
     *
     * @param batch redacted events
     * @return completes when the batch was accepted
     */
    Mono<Void> send(List<GatewayAuditEvent> batch);
}
