package com.heuristiq.gateway.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded in-memory queue between request threads and the batcher. {@link #offer} never blocks and never fails:
 * when the queue is over its event count or byte budget the OLDEST events are dropped (and counted), so recent
 * activity is what survives an audit-service outage. The lock is held only for a few pointer operations.
 */
public final class AuditEventQueue {

    private final int capacity;
    private final long maxBytes;
    private final ArrayDeque<GatewayAuditEvent> events = new ArrayDeque<>();
    private final ArrayDeque<Long> sizes = new ArrayDeque<>();
    private final AtomicLong dropped = new AtomicLong();
    private long bytes;

    /**
     * Creates a queue.
     *
     * @param capacity maximum number of queued events
     * @param maxBytes approximate maximum memory of the queued events
     */
    public AuditEventQueue(int capacity, long maxBytes) {
        this.capacity = Math.max(1, capacity);
        this.maxBytes = Math.max(1, maxBytes);
    }

    /**
     * Adds an event, dropping the oldest ones while the queue is over budget.
     *
     * @param event the event
     * @return how many older events were dropped to make room (0 normally)
     */
    public synchronized int offer(GatewayAuditEvent event) {
        long size = event.approximateBytes();
        events.addLast(event);
        sizes.addLast(size);
        bytes += size;
        int droppedNow = 0;
        while ((events.size() > capacity || bytes > maxBytes) && events.size() > 1) {
            events.pollFirst();
            bytes -= sizes.pollFirst();
            droppedNow++;
        }
        if (droppedNow > 0) {
            dropped.addAndGet(droppedNow);
        }
        return droppedNow;
    }

    /**
     * @param max most events to take
     * @return up to {@code max} events, oldest first, removed from the queue
     */
    public synchronized List<GatewayAuditEvent> drain(int max) {
        List<GatewayAuditEvent> out = new ArrayList<>(Math.min(max, events.size()));
        while (out.size() < max && !events.isEmpty()) {
            out.add(events.pollFirst());
            bytes -= sizes.pollFirst();
        }
        return out;
    }

    /** @return events currently queued */
    public synchronized int size() {
        return events.size();
    }

    /** @return approximate bytes currently queued */
    public synchronized long bytes() {
        return bytes;
    }

    /** @return total events dropped because the queue was full */
    public long dropped() {
        return dropped.get();
    }
}
