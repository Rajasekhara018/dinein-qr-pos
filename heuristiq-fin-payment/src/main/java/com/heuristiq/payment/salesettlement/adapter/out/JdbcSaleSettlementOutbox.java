package com.heuristiq.payment.salesettlement.adapter.out;

import com.heuristiq.payment.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementOutbox;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** JDBC adapter of {@link SaleSettlementOutbox}; enqueue joins the caller's transaction (the payment's status change). */
@Component
public class JdbcSaleSettlementOutbox implements SaleSettlementOutbox {

    private final JdbcTemplate jdbc;

    public JdbcSaleSettlementOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean enqueue(String eventId, SaleSettlementAction action, String idempotencyKey, String paymentId, String tenantId,
                           String reason) {
        return jdbc.update(SaleSettlementSql.ENQUEUE, idempotencyKey, eventId, action.name(), paymentId, tenantId, reason) == 1;
    }

    @Override
    public List<SaleSettlementRequest> due(Instant now, int limit) {
        return jdbc.query(SaleSettlementSql.DUE, (rs, n) -> new SaleSettlementRequest(rs.getLong("id"), rs.getString("event_id"),
                SaleSettlementAction.valueOf(rs.getString("action")), rs.getString("idempotency_key"), rs.getString("payment_id"),
                rs.getString("tenant_id"), rs.getString("reason"), rs.getInt("attempts"), instant(rs.getTimestamp("created_at"))),
                Timestamp.from(now), limit);
    }

    @Override
    public void finish(long id, SaleSettlementState state, String outcome, String walletJournalId, String lastError, Instant at) {
        jdbc.update(SaleSettlementSql.FINISH, state.name(), outcome, walletJournalId, lastError, Timestamp.from(at), id);
    }

    @Override
    public void retryLater(long id, int attempts, String lastError, Instant nextAttemptAt) {
        jdbc.update(SaleSettlementSql.RETRY_LATER, attempts, lastError, Timestamp.from(nextAttemptAt), id);
    }

    @Override
    public void awaitSale(long id, String lastError, Instant nextCheckAt) {
        jdbc.update(SaleSettlementSql.AWAIT_SALE, lastError, Timestamp.from(nextCheckAt), id);
    }

    @Override
    public boolean salePaidAfterAll(String paymentId) {
        Long count = jdbc.queryForObject(SaleSettlementSql.SALE_PAID_AFTER_ALL, Long.class, paymentId);
        return count != null && count > 0;
    }

    private static Instant instant(Timestamp at) {
        return at == null ? null : at.toInstant();
    }
}
