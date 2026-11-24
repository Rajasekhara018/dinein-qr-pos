package com.heuristiq.paymentservice.events;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The outbox against a real PostgreSQL, because its guarantees live in the claim SQL. Only Kafka is a fake. */
class DurableEventOutboxTest {

    private static final String TOPIC = "test-topic";
    /** Sends start only while this much of a run is left; short so the tests stay quick. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(1);

    /** Stands for a process that dies mid-batch: an Error, which nothing in the drain handles. */
    private static final class InstanceDied extends Error {
    }

    private static EmbeddedPostgres postgres;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactions;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final List<String> sent = new ArrayList<>();

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        Flyway.configure().dataSource(postgres.getPostgresDatabase()).locations("classpath:db/migration")
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
        jdbc = new JdbcTemplate(postgres.getPostgresDatabase());
        transactions = new DataSourceTransactionManager(postgres.getPostgresDatabase());
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM integration_outbox");
        acknowledgeEverySend();
    }

    private void acknowledgeEverySend() {
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            return CompletableFuture.completedFuture(null);
        }).when(kafka).send(anyString(), anyString(), anyString());
    }

    private DurableEventOutbox outbox(int batchSize) {
        return outbox(batchSize, Duration.ofSeconds(5));
    }

    private DurableEventOutbox outbox(int batchSize, Duration deadline) {
        return new DurableEventOutbox(jdbc, kafka, transactions,
                new OutboxProperties(true, batchSize, deadline, deadline.plusSeconds(10), SEND_TIMEOUT));
    }

    private void enqueue(String eventId, String key) {
        outbox(1).enqueue(eventId, TOPIC, key, eventId);
    }

    private void failSendOf(String payload) {
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            return CompletableFuture.failedFuture(new IllegalStateException("broker refused"));
        }).when(kafka).send(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(payload));
    }

    private void makeAllDue() {
        jdbc.update("UPDATE integration_outbox SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
    }

    private List<String> unpublished() {
        return jdbc.queryForList("SELECT event_id FROM integration_outbox WHERE published_at IS NULL ORDER BY id", String.class);
    }

    private int attempts(String eventId) {
        return jdbc.queryForObject("SELECT attempts FROM integration_outbox WHERE event_id = ?", Integer.class, eventId);
    }

    @Test
    void sendsABatchInIdOrderAndMarksItPublished() {
        enqueue("a1", "A");
        enqueue("b1", "B");
        enqueue("c1", "C");

        outbox(200).drain();

        assertThat(sent).containsExactly("a1", "b1", "c1");
        assertThat(unpublished()).isEmpty();
    }

    @Test
    void aFailedRowHoldsBackLaterRowsOfItsKeyButNotOtherKeysUntilItIsPublished() {
        enqueue("a1", "A");
        enqueue("a2", "A");
        enqueue("b1", "B");
        failSendOf("a1");

        outbox(200).drain();

        assertThat(sent).containsExactly("a1", "b1");
        assertThat(unpublished()).containsExactly("a1", "a2");
        assertThat(attempts("a1")).isEqualTo(1);
        assertThat(attempts("a2")).isZero();

        // still backing off: nothing moves, and the later row must not overtake it
        outbox(200).drain();
        assertThat(sent).containsExactly("a1", "b1");

        // the failed row goes first when due, and only then the row behind it
        acknowledgeEverySend();
        makeAllDue();
        outbox(200).drain();
        assertThat(sent).containsExactly("a1", "b1", "a1");
        outbox(200).drain();
        assertThat(sent).containsExactly("a1", "b1", "a1", "a2");
        assertThat(unpublished()).isEmpty();
    }

    @Test
    void aBatchNeverHoldsTwoRowsOfOneKey() {
        enqueue("a1", "A");
        enqueue("a2", "A");
        enqueue("a3", "A");

        outbox(200).drain();

        assertThat(sent).containsExactly("a1");
    }

    @Test
    void aFullBatchDrainsAgainStraightAway() {
        for (int i = 1; i <= 5; i++) {
            enqueue("e" + i, "key-" + i);
        }

        outbox(2).drain();

        assertThat(sent).containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(unpublished()).isEmpty();
    }

    @Test
    void aFailureEndsTheRunSoABrokerOutageDoesNotWalkTheWholeBacklog() {
        for (int i = 1; i <= 4; i++) {
            enqueue("e" + i, "key-" + i);
        }
        failSendOf("e1");

        outbox(2).drain();

        assertThat(sent).containsExactly("e1", "e2");
    }

    @Test
    void aSendThatThrowsCountsAsAFailedAttempt() {
        enqueue("a1", "A");
        doThrow(new IllegalStateException("no metadata")).when(kafka).send(anyString(), anyString(), anyString());

        outbox(200).drain();

        assertThat(unpublished()).containsExactly("a1");
        assertThat(attempts("a1")).isEqualTo(1);
    }

    @Test
    void aSendStillUnsettledAtTheDeadlineStaysClaimedUntilTheLeaseEndsAndHoldsItsKey() {
        enqueue("a1", "A");
        enqueue("a2", "A");
        doReturn(new CompletableFuture<>()).when(kafka).send(anyString(), anyString(), anyString());

        outbox(200, Duration.ofSeconds(3)).drain();

        // it may still be delivered: not counted as failed, not handed back, so no other run can send it again yet
        assertThat(unpublished()).containsExactly("a1", "a2");
        assertThat(attempts("a1")).isZero();
        acknowledgeEverySend();
        sent.clear();
        outbox(200).drain();
        assertThat(sent).isEmpty();

        makeAllDue(); // the lease has run out
        outbox(200).drain();
        assertThat(sent).containsExactly("a1");
    }

    @Test
    void aRunWhoseLeaseRanOutCannotUndoTheClaimOfTheRunThatTookTheRowOver() {
        enqueue("a1", "A");
        DurableEventOutbox other = outbox(200);
        List<String> sentByOther = new ArrayList<>();
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            if (sent.size() == 1) {
                // this run is slow: its lease runs out and another instance claims the row and sends it again
                makeAllDue();
                int before = sent.size();
                other.drain();
                sentByOther.addAll(sent.subList(before, sent.size()));
                return CompletableFuture.failedFuture(new IllegalStateException("late broker failure"));
            }
            return CompletableFuture.completedFuture(null);
        }).when(kafka).send(anyString(), anyString(), anyString());

        outbox(200).drain();

        assertThat(sentByOther).containsExactly("a1");
        // the other run published it; the late failure of the first run changed nothing
        assertThat(unpublished()).isEmpty();
        assertThat(attempts("a1")).isZero();
    }

    @Test
    void aLateRunCannotReleaseARowAnotherRunHasClaimedSince() {
        enqueue("a1", "A");
        enqueue("b1", "B");
        doReturn(new CompletableFuture<>()).when(kafka).send(anyString(), anyString(), anyString());
        // the first instance claims both rows; then another holds b1 under a newer lease
        jdbc.update("UPDATE integration_outbox SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '1 hour' WHERE event_id = 'b1'");
        java.time.OffsetDateTime othersLease = jdbc.queryForObject(
                "SELECT next_attempt_at FROM integration_outbox WHERE event_id = 'b1'", java.time.OffsetDateTime.class);

        outbox(200, Duration.ofSeconds(3)).drain();

        assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM integration_outbox WHERE event_id = 'b1'",
                java.time.OffsetDateTime.class)).isEqualTo(othersLease);
    }

    @Test
    void aSecondInstanceDrainingMeanwhileLeavesClaimedRowsAndTheirKeysAlone() {
        enqueue("a1", "A");
        enqueue("a2", "A");
        enqueue("b1", "B");
        DurableEventOutbox other = outbox(200);
        List<String> seenByOther = new ArrayList<>();
        doAnswer(call -> {
            String payload = call.getArgument(2);
            sent.add(payload);
            if (payload.equals("a1")) {
                // the first instance is mid-send with a1 and b1 claimed: the other one runs now
                int before = sent.size();
                other.drain();
                seenByOther.addAll(sent.subList(before, sent.size()));
            }
            return CompletableFuture.completedFuture(null);
        }).when(kafka).send(anyString(), anyString(), anyString());

        outbox(200).drain();

        assertThat(seenByOther).isEmpty();
        assertThat(sent).containsExactly("a1", "b1");
    }

    @Test
    void theRowsOfACrashedInstanceComeBackWhenTheLeaseEnds() {
        enqueue("a1", "A");
        doThrow(new InstanceDied()).when(kafka).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> outbox(200).drain()).isInstanceOf(InstanceDied.class);

        acknowledgeEverySend();
        outbox(200).drain();
        assertThat(sent).isEmpty();
        assertThat(attempts("a1")).isZero();

        makeAllDue();
        outbox(200).drain();
        assertThat(sent).containsExactly("a1");
    }

    @Test
    void aDisabledOutboxSendsNothing() {
        enqueue("a1", "A");

        new DurableEventOutbox(jdbc, kafka, transactions,
                new OutboxProperties(false, 200, Duration.ofSeconds(2), Duration.ofSeconds(3), SEND_TIMEOUT)).drain();

        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    void aDeadlineAsLongAsTheLeaseIsRefused() {
        assertThatThrownBy(() -> new OutboxProperties(true, 200, Duration.ofSeconds(30), Duration.ofSeconds(30), SEND_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(true, 0, Duration.ofSeconds(2), Duration.ofSeconds(3), SEND_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxProperties(true, 200, Duration.ofSeconds(20), Duration.ofSeconds(30), Duration.ofSeconds(20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startUpFailsWhenTheProducerCanKeepASendOpenLongerThanTheSendTimeout() {
        OutboxProperties defaults = new OutboxProperties(true, 200, Duration.ofSeconds(20), Duration.ofSeconds(30), Duration.ofSeconds(15));
        org.springframework.kafka.core.ProducerFactory<String, String> factory = mock(org.springframework.kafka.core.ProducerFactory.class);

        doReturn(java.util.Map.of()).when(factory).getConfigurationProperties();
        assertThatThrownBy(() -> DurableEventOutbox.requireSendsToSettleWithinTheSendTimeout(factory, defaults))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("180000");

        doReturn(java.util.Map.of("max.block.ms", "3000", "delivery.timeout.ms", "12000")).when(factory).getConfigurationProperties();
        DurableEventOutbox.requireSendsToSettleWithinTheSendTimeout(factory, defaults);
    }
}
