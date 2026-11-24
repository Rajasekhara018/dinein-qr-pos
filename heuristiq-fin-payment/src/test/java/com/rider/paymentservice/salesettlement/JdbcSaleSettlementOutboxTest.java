package com.heuristiq.paymentservice.salesettlement;

import com.heuristiq.paymentservice.salesettlement.adapter.out.JdbcSaleSettlementOutbox;
import com.heuristiq.paymentservice.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.paymentservice.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.paymentservice.salesettlement.domain.SaleSettlementState;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The request store against a real PostgreSQL (V26), because ordering and the paid-after-all guard live in its SQL. */
class JdbcSaleSettlementOutboxTest {

    private static EmbeddedPostgres postgres;
    private static JdbcTemplate jdbc;
    private JdbcSaleSettlementOutbox outbox;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        Flyway.configure().dataSource(postgres.getPostgresDatabase()).locations("classpath:db/migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
        jdbc = new JdbcTemplate(postgres.getPostgresDatabase());
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sale_settlement_requests");
        jdbc.update("DELETE FROM payments");
        outbox = new JdbcSaleSettlementOutbox(jdbc);
    }

    private void payment(String id, String status, String rideId, String sourceService, String sourceReference) {
        // an external payment's ride_id is "<sourceService>:<sourceReference>", as PaymentService stores it
        String storedRideId = sourceService == null ? rideId : sourceService + ":" + sourceReference;
        jdbc.update("INSERT INTO payments (internal_payment_id, customer_id, method, idempotency_key, status, ride_id, source_service, "
                + "source_reference, amount, currency, created_at, updated_at) VALUES (?, 'c-1', 'RAZORPAY', ?, ?, ?, ?, ?, 100.00, 'INR', "
                + "now(), now())", id, "key-" + id, status, storedRideId, sourceService, sourceReference);
    }

    @Test
    void aRepeatOfTheSameKeyIsStoredOnce() {
        assertThat(outbox.enqueue("ride-completed:r-1", SaleSettlementAction.SETTLE, "sale-settle:ride-completed:r-1", "p-1", "t1", null))
                .isTrue();
        assertThat(outbox.enqueue("ride-completed:r-1", SaleSettlementAction.SETTLE, "sale-settle:ride-completed:r-1", "p-2", "t1", null))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_settlement_requests", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlyTheFirstPendingRequestOfASaleIsDue() {
        outbox.enqueue("e-1", SaleSettlementAction.CANCEL, "sale-cancel:e-1", "p-1", "t1", "failed");
        outbox.enqueue("e-1", SaleSettlementAction.SETTLE, "sale-settle:e-1", "p-1", "t1", null);
        outbox.enqueue("e-2", SaleSettlementAction.SETTLE, "sale-settle:e-2", "p-2", "t1", null);

        List<SaleSettlementRequest> due = outbox.due(Instant.now().plusSeconds(1), 10);

        assertThat(due).extracting(SaleSettlementRequest::idempotencyKey).containsExactly("sale-cancel:e-1", "sale-settle:e-2");
        assertThat(due.get(0).tenantId()).isEqualTo("t1");
        assertThat(due.get(0).reason()).isEqualTo("failed");

        outbox.finish(due.get(0).id(), SaleSettlementState.SUPERSEDED, "PAYMENT_CAPTURED", null, null, Instant.now());
        assertThat(outbox.due(Instant.now().plusSeconds(1), 10)).extracting(SaleSettlementRequest::idempotencyKey)
                .containsExactly("sale-settle:e-1", "sale-settle:e-2");
    }

    @Test
    void aRequestWaitingForItsBackoffIsNotDue() {
        outbox.enqueue("e-1", SaleSettlementAction.SETTLE, "sale-settle:e-1", "p-1", "t1", null);
        long id = outbox.due(Instant.now().plusSeconds(1), 10).get(0).id();

        outbox.retryLater(id, 1, "SALE_NOT_FOUND_YET", Instant.now().plusSeconds(600));

        assertThat(outbox.due(Instant.now().plusSeconds(1), 10)).isEmpty();
        SaleSettlementRequest later = outbox.due(Instant.now().plusSeconds(601), 10).get(0);
        assertThat(later.attempts()).isEqualTo(1);
    }

    @Test
    void waitingForTheSaleMovesTheNextCheckButCountsNoAttemptAndTheRequestKnowsItsAge() {
        Instant before = Instant.now().minusSeconds(5);
        outbox.enqueue("e-1", SaleSettlementAction.SETTLE, "sale-settle:e-1", "p-1", "t1", null);
        SaleSettlementRequest first = outbox.due(Instant.now().plusSeconds(1), 10).get(0);
        assertThat(first.createdAt()).isAfter(before);

        outbox.awaitSale(first.id(), "AWAITING_SALE", Instant.now().plusSeconds(120));

        assertThat(outbox.due(Instant.now().plusSeconds(1), 10)).isEmpty();
        SaleSettlementRequest later = outbox.due(Instant.now().plusSeconds(121), 10).get(0);
        assertThat(later.attempts()).isZero();
        assertThat(jdbc.queryForObject("SELECT last_error FROM sale_settlement_requests WHERE id = ?", String.class, first.id()))
                .isEqualTo("AWAITING_SALE");

        outbox.finish(first.id(), SaleSettlementState.DONE, "SETTLED", null, null, Instant.now());
        outbox.awaitSale(first.id(), "AWAITING_SALE", Instant.now());
        assertThat(jdbc.queryForObject("SELECT status FROM sale_settlement_requests WHERE id = ?", String.class, first.id()))
                .as("a finished request is left alone").isEqualTo("DONE");
    }

    @Test
    void aFinishedRequestIsNeverDueAgain() {
        outbox.enqueue("e-1", SaleSettlementAction.SETTLE, "sale-settle:e-1", "p-1", "t1", null);
        long id = outbox.due(Instant.now().plusSeconds(1), 10).get(0).id();

        outbox.finish(id, SaleSettlementState.DONE, "SETTLED", "j-1", null, Instant.now());

        assertThat(outbox.due(Instant.now().plusSeconds(1), 10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT wallet_journal_id FROM sale_settlement_requests WHERE id = ?", String.class, id))
                .isEqualTo("j-1");
    }

    @Test
    void aSaleIsPaidAfterAllWhenAnotherPaymentOfTheSameRideOrReferenceWasCaptured() {
        payment("p-failed", "FAILED", "r-1", null, null);
        payment("p-retry", "CAPTURED", "r-1", null, null);
        payment("m-failed", "EXPIRED", null, "matrimonial-api", "txn-1");
        payment("m-other", "CAPTURED", null, "matrimonial-api", "txn-2");
        payment("p-alone", "FAILED", "r-2", null, null);

        assertThat(outbox.salePaidAfterAll("p-failed")).isTrue();
        assertThat(outbox.salePaidAfterAll("m-failed")).as("another transaction's capture does not count").isFalse();
        assertThat(outbox.salePaidAfterAll("p-alone")).isFalse();
    }
}
