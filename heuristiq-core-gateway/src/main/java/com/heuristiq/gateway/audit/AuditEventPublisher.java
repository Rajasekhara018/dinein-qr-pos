package com.heuristiq.gateway.audit;

import static com.heuristiq.gateway.audit.AuditConstants.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Clock;
import java.time.Instant;

/**
 * Records one ACCESS audit event per request: who (user id, roles), which privilege(s) matched or that the call was
 * denied, feature and module from the privilege catalog, method, normalised path, status, duration, correlation id,
 * IP, user agent and channel, plus (configurable) redacted headers and capped bodies.
 *
 * <p><b>Order -80</b>: this filter wraps {@code PrivilegeBasedAccessFilter} (-79). A denied request is answered
 * inside that filter and never reaches a filter ordered after it, so the event is built in {@code doFinally} here,
 * after the whole chain (privilege check, routing, response) finished, reading what the privilege filter left in
 * the exchange attributes ({@link AuditExchangeAttributes}). Requests refused earlier, by the JWT filter (401,
 * unknown caller), are not audited here.
 *
 * <p>It never blocks or fails a request: building the event is in-memory work, the event goes to a bounded queue
 * that drops its oldest entries when full, and any error in this class is swallowed. Body capture only observes
 * the data buffers as they pass ({@link AuditCaptureDecorators}); it does not buffer, delay or alter them.
 */
@Slf4j
public class AuditEventPublisher implements GlobalFilter, Ordered {

    private final AuditProperties properties;
    private final AuditPathRules rules;
    private final AuditEventFactory factory;
    private final AuditEventQueue queue;
    private final AuditBatcher batcher;
    private final Clock clock;

    /**
     * Creates the publisher.
     *
     * @param properties audit settings
     * @param rules      path rules
     * @param factory    event builder
     * @param queue      bounded queue
     * @param batcher    background sender, poked when a full batch is waiting
     * @param clock      time source (event time and duration)
     */
    public AuditEventPublisher(AuditProperties properties, AuditPathRules rules, AuditEventFactory factory,
                               AuditEventQueue queue, AuditBatcher batcher, Clock clock) {
        this.properties = properties;
        this.rules = rules;
        this.factory = factory;
        this.queue = queue;
        this.batcher = batcher;
        this.clock = clock;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod() == null ? null : exchange.getRequest().getMethod().name();
        if (rules.skip(path, method)) {
            return chain.filter(exchange);
        }
        Instant started = clock.instant();
        long startMillis = clock.millis();

        ServerWebExchange target = exchange;
        AuditEventFactory.Captured captured = null;
        try {
            if ((properties.isCaptureBodies()) && !rules.captureExcluded(path) && !rules.bodySkipped(path)) {
                AuditBodyCapture request = new AuditBodyCapture(properties.getMaxBodyBytes());
                AuditBodyCapture response = new AuditBodyCapture(properties.getMaxBodyBytes());
                target = exchange.mutate()
                        .request(AuditCaptureDecorators.request(exchange.getRequest(), request))
                        .response(AuditCaptureDecorators.response(exchange.getResponse(), response)).build();
                captured = new AuditEventFactory.Captured(request, response);
            } else if (properties.isCaptureHeaders() && !rules.captureExcluded(path)) {
                captured = new AuditEventFactory.Captured(null, null);
            }
        } catch (RuntimeException e) {
            target = exchange;
            captured = null;
        }
        final AuditEventFactory.Captured capturedFinal = captured;
        final ServerWebExchange finalTarget = target;
        return chain.filter(target).doFinally(signal -> publish(exchange, finalTarget, signal, started,
                startMillis, capturedFinal));
    }

    private void publish(ServerWebExchange exchange, ServerWebExchange target, SignalType signal, Instant started,
                         long startMillis, AuditEventFactory.Captured captured) {
        try {
            int status = target.getResponse().getStatusCode() == null ? 0 : target.getResponse().getStatusCode().value();
            if (status == 0 && signal == SignalType.ON_COMPLETE) {
                status = 200;
            } else if (status == 0 && signal == SignalType.ON_ERROR) {
                status = 500;
            }
            long durationMs = clock.millis() - startMillis;
            GatewayAuditEvent event = factory.build(target, status, started, durationMs, captured);
            queue.offer(event);
            if (queue.size() >= properties.getBatchSize()) {
                batcher.requestFlush();
            }
        } catch (Throwable t) {
            log.debug("audit event could not be built: {}", t.toString());
        }
    }
}
