package com.heuristiq.paymentservice.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Durable outbox tuning ({@code rider.outbox.*}).
 *
 * @param enabled       turns the drain job off (tests, or an instance that must not publish)
 * @param batchSize     rows claimed and sent per batch
 * @param batchDeadline time budget of one drain run; must stay below the drain job's lock and the lease
 * @param lease         how long a claimed row stays hidden from other runs, which is also how soon the rows of a
 *                      crashed instance are retried
 * @param sendTimeout   longest time the Kafka client may take to settle one send, either way: at least the producer's
 *                      {@code max.block.ms} plus {@code delivery.timeout.ms} (checked at start-up). A send is only
 *                      started while this much of the run's budget is left, so every send has definitely succeeded
 *                      or failed before the deadline, and a row recorded as failed cannot still be delivered later.
 */
@ConfigurationProperties(prefix = "rider.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("200") int batchSize,
        @DefaultValue("20s") Duration batchDeadline,
        @DefaultValue("30s") Duration lease,
        @DefaultValue("15s") Duration sendTimeout) {

    public OutboxProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("rider.outbox.batch-size must be at least 1");
        }
        if (batchDeadline.compareTo(lease) >= 0) {
            throw new IllegalArgumentException("rider.outbox.batch-deadline must be shorter than rider.outbox.lease");
        }
        if (sendTimeout.compareTo(batchDeadline) >= 0) {
            throw new IllegalArgumentException("rider.outbox.send-timeout must be shorter than rider.outbox.batch-deadline");
        }
    }
}
