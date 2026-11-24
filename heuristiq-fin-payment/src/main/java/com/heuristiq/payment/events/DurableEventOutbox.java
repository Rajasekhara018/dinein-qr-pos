package com.heuristiq.paymentservice.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * At-least-once delivery; consumers must deduplicate eventId after a broker ACK / DB crash.
 * <p>
 * Each batch runs in three steps, and no transaction is open during the Kafka send: claim rows under a lease
 * (see {@link OutboxConstants#CLAIM_BATCH}), send all of them and await the acknowledgements, then record every
 * outcome in one short transaction. A row that fails is retried with backoff, and until it is published no later row
 * of its key can be claimed. A send is only started while the Kafka client is bound to settle it before the run's
 * deadline ({@link OutboxProperties#sendTimeout()}), so a send recorded as failed can never arrive later behind the
 * key's next row. A send still unsettled at the deadline anyway is not recorded at all: its row stays claimed until
 * the lease runs out, by which time the client has given up on it. Failure and release only apply while this run
 * still holds the lease. An instance that dies mid-batch leaves its rows to be retried when the lease runs out.
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@Slf4j
public class DurableEventOutbox {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final OutboxProperties properties;

    public DurableEventOutbox(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
            PlatformTransactionManager manager, OutboxProperties properties) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(60);
        this.properties = properties;
        requireSendsToSettleWithinTheSendTimeout(kafka.getProducerFactory(), properties);
    }

    /** Fails start-up when the producer may keep a send open longer than the outbox assumes. */
    static void requireSendsToSettleWithinTheSendTimeout(ProducerFactory<?, ?> factory, OutboxProperties properties) {
        if (factory == null || !properties.enabled()) return;
        Map<String, Object> config = factory.getConfigurationProperties();
        long settleMs = millis(config.get(OutboxConstants.PRODUCER_MAX_BLOCK_MS), OutboxConstants.KAFKA_DEFAULT_MAX_BLOCK_MS)
                + millis(config.get(OutboxConstants.PRODUCER_DELIVERY_TIMEOUT_MS), OutboxConstants.KAFKA_DEFAULT_DELIVERY_TIMEOUT_MS);
        if (settleMs > properties.sendTimeout().toMillis()) {
            throw new IllegalStateException("Kafka max.block.ms + delivery.timeout.ms (" + settleMs
                    + " ms) exceeds rider.outbox.send-timeout (" + properties.sendTimeout().toMillis() + " ms)");
        }
    }

    private static long millis(Object configured, long kafkaDefault) {
        return configured == null ? kafkaDefault : Long.parseLong(configured.toString().trim());
    }

    public void enqueue(String id, String topic, String key, String json) {
        jdbc.update(OutboxConstants.ENQUEUE, id, topic, key, json);
    }

    /**
     * Runs on every instance (this service has no scheduler lock): row claims keep instances off the same row and
     * the NOT EXISTS rule off the same key. Full batches repeat until the batch deadline.
     */
    @Scheduled(fixedDelayString = "${rider.outbox.poll-ms:250}")
    public void drain() {
        if (!properties.enabled()) return;
        long deadline = System.nanoTime() + properties.batchDeadline().toNanos();
        long lastSendStart = deadline - properties.sendTimeout().toNanos();
        try {
            boolean more = true;
            while (more && System.nanoTime() < lastSendStart) {
                more = drainBatch(lastSendStart, deadline);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox poll failed; pending records retained: {}", e.getMessage());
        }
    }

    /** @return true when the batch was full and fully delivered, so more rows may be waiting straight away */
    private boolean drainBatch(long lastSendStart, long deadline) {
        List<OutboxRow> claimed = claim();
        if (claimed.isEmpty()) return false;

        List<OutboxRow> unsent = new ArrayList<>();
        List<OutboxRow> failed = new ArrayList<>();
        Map<OutboxRow, CompletableFuture<?>> sent = new LinkedHashMap<>();
        for (OutboxRow row : claimed) {
            if (System.nanoTime() >= lastSendStart) {
                unsent.add(row);
                continue;
            }
            try {
                sent.put(row, kafka.send(row.topic(), row.aggregateKey(), row.payload()));
            } catch (RuntimeException e) {
                failed.add(row);
            }
        }
        await(sent.values(), deadline);

        List<OutboxRow> published = new ArrayList<>();
        int unsettled = 0;
        for (Map.Entry<OutboxRow, CompletableFuture<?>> entry : sent.entrySet()) {
            CompletableFuture<?> ack = entry.getValue();
            if (!ack.isDone()) {
                unsettled++; // may still be delivered: leave it claimed until the lease runs out
            } else if (ack.isCompletedExceptionally()) {
                failed.add(entry.getKey());
            } else {
                published.add(entry.getKey());
            }
        }
        record(published, failed, unsent);
        if (!failed.isEmpty() || unsettled > 0) {
            OutboxRow first = failed.isEmpty() ? claimed.get(0) : failed.get(0);
            log.warn("Outbox delivery pending: {} failed, {} unsettled at the deadline; first eventId={} topic={}",
                    failed.size(), unsettled, first.eventId(), first.topic());
        }
        return claimed.size() == properties.batchSize() && published.size() == claimed.size();
    }

    private List<OutboxRow> claim() {
        List<OutboxRow> rows = transaction.execute(status -> jdbc.query(OutboxConstants.CLAIM_BATCH,
                (rs, rowNum) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"), rs.getString("topic"),
                        rs.getString("aggregate_key"), rs.getString("payload"), rs.getInt("attempts"),
                        rs.getObject("next_attempt_at", OffsetDateTime.class)),
                properties.batchSize(), properties.lease().toMillis()));
        if (rows == null) return List.of();
        return rows.stream().sorted(Comparator.comparingLong(OutboxRow::id)).toList();
    }

    /** Waits until every send has answered or the deadline passes; each outcome is read from its future afterwards. */
    private void await(Collection<CompletableFuture<?>> acks, long deadline) {
        try {
            CompletableFuture.allOf(acks.toArray(CompletableFuture[]::new))
                    .get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Outbox batch ended with failed or unanswered sends: {}", e.toString());
        }
    }

    private void record(List<OutboxRow> published, List<OutboxRow> failed, List<OutboxRow> unsent) {
        transaction.executeWithoutResult(status -> {
            jdbc.batchUpdate(OutboxConstants.MARK_PUBLISHED, published, published.size(),
                    (ps, row) -> ps.setLong(1, row.id()));
            jdbc.batchUpdate(OutboxConstants.RECORD_FAILURE, failed, failed.size(), (ps, row) -> {
                int attempts = row.attempts() + 1;
                ps.setInt(1, attempts);
                ps.setLong(2, retryDelaySeconds(attempts));
                ps.setLong(3, row.id());
                ps.setObject(4, row.leaseUntil());
            });
            jdbc.batchUpdate(OutboxConstants.RELEASE_UNSENT, unsent, unsent.size(), (ps, row) -> {
                ps.setLong(1, row.id());
                ps.setObject(2, row.leaseUntil());
            });
        });
    }

    private static long retryDelaySeconds(int attempts) {
        return Math.min(OutboxConstants.MAX_RETRY_DELAY_SECONDS,
                1L << Math.min(attempts, OutboxConstants.MAX_RETRY_DELAY_DOUBLINGS));
    }

    /** One claimed row of {@code integration_outbox}; {@code leaseUntil} is this run's claim token. */
    private record OutboxRow(long id, String eventId, String topic, String aggregateKey, String payload, int attempts,
                             OffsetDateTime leaseUntil) {
    }
}
