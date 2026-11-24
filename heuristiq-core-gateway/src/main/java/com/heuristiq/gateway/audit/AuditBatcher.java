package com.heuristiq.gateway.audit;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Background sender. Every {@code flush-interval-ms} (and as soon as a full batch is waiting) it takes up to
 * {@code batch-size} events off the {@link AuditEventQueue}, redacts them, and hands them to the
 * {@link AuditEventSink}. One batch is in flight at a time; a failed batch is dropped, never retried, and logged
 * as a rate-limited warning, so an audit-service outage costs memory-bounded drops and nothing else.
 */
@Slf4j
public class AuditBatcher {

    private final AuditEventQueue queue;
    private final AuditEventSink sink;
    private final AuditEventRedactor redactor;
    private final AuditProperties properties;
    private final LongSupplier clockMillis;
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicBoolean flushQueued = new AtomicBoolean();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failedEvents = new AtomicLong();
    private final AtomicLong lastWarnMillis = new AtomicLong(Long.MIN_VALUE);
    private Scheduler scheduler;
    private Disposable periodic;

    /**
     * Creates the batcher.
     *
     * @param queue       events to send
     * @param sink        destination
     * @param redactor    credential remover, applied to every event before it is sent
     * @param properties  audit settings
     * @param clockMillis time source for the warning rate limit
     */
    public AuditBatcher(AuditEventQueue queue, AuditEventSink sink, AuditEventRedactor redactor,
                        AuditProperties properties, LongSupplier clockMillis) {
        this.queue = queue;
        this.sink = sink;
        this.redactor = redactor;
        this.properties = properties;
        this.clockMillis = clockMillis;
    }

    /** Starts the periodic flush on a daemon thread. */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Schedulers.newSingle("audit-batcher", true);
        long interval = Math.max(100, properties.getFlushIntervalMs());
        periodic = scheduler.schedulePeriodically(this::tick, interval, interval, TimeUnit.MILLISECONDS);
    }

    /** Stops the periodic flush and tries one last send of what is queued. */
    public synchronized void stop() {
        if (periodic != null) {
            periodic.dispose();
        }
        try {
            flushOnce().block(AuditConstants.SHUTDOWN_FLUSH_TIMEOUT);
        } catch (RuntimeException ignored) {
            // shutting down: whatever is left is dropped
        }
        if (scheduler != null) {
            scheduler.dispose();
            scheduler = null;
        }
    }

    /** Asks for a flush soon (called when a full batch is waiting); never blocks the caller. */
    public void requestFlush() {
        Scheduler s = scheduler;
        if (s != null && flushQueued.compareAndSet(false, true)) {
            try {
                s.schedule(() -> {
                    flushQueued.set(false);
                    tick();
                });
            } catch (RuntimeException rejected) {
                flushQueued.set(false);
            }
        }
    }

    private void tick() {
        flushOnce().subscribe(n -> { }, e -> log.debug("audit flush failed", e));
    }

    /**
     * Sends one batch if one is waiting and none is in flight.
     *
     * @return the number of events handed to the sink (0 when idle or when the send failed)
     */
    public Mono<Integer> flushOnce() {
        if (!sending.compareAndSet(false, true)) {
            return Mono.just(0);
        }
        List<GatewayAuditEvent> raw = queue.drain(Math.max(1, properties.getBatchSize()));
        if (raw.isEmpty()) {
            sending.set(false);
            return Mono.just(0);
        }
        List<GatewayAuditEvent> batch = new ArrayList<>(raw.size());
        for (GatewayAuditEvent e : raw) {
            try {
                batch.add(redactor.redact(e));
            } catch (RuntimeException redactionFailure) {
                // an event that can not be redacted is never sent
                failedEvents.incrementAndGet();
            }
        }
        if (batch.isEmpty()) {
            sending.set(false);
            return Mono.just(0);
        }
        return Mono.defer(() -> sink.send(batch))
                .thenReturn(batch.size())
                .doOnNext(n -> sent.addAndGet(n))
                .onErrorResume(error -> {
                    failedEvents.addAndGet(batch.size());
                    warnLimited(batch.size(), error);
                    return Mono.just(0);
                })
                .doFinally(signal -> {
                    sending.set(false);
                    if (queue.size() >= properties.getBatchSize()) {
                        requestFlush();
                    }
                });
    }

    private void warnLimited(int dropped, Throwable error) {
        long now = clockMillis.getAsLong();
        long last = lastWarnMillis.get();
        if ((last == Long.MIN_VALUE || now - last >= properties.getWarnIntervalMs()) && lastWarnMillis.compareAndSet(last, now)) {
            log.warn("Audit service unavailable: dropped {} audit event(s) ({}). Further warnings are suppressed for {} ms.",
                    dropped, error.getClass().getSimpleName(), properties.getWarnIntervalMs());
        }
    }

    /** @return events successfully handed to the audit service */
    public long sentCount() {
        return sent.get();
    }

    /** @return events dropped because a send failed or redaction failed */
    public long failedCount() {
        return failedEvents.get();
    }
}
