package com.heuristiq.payment.refundaccounting.adapter.out;

import com.heuristiq.payment.refundaccounting.domain.RefundAccounting;
import static com.heuristiq.payment.refundaccounting.domain.RefundAccountingConstants.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRefundAccountingStore implements RefundAccounting.Store {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcRefundAccountingStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void enqueue(RefundAccounting.Posting p) {
        var args = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", p.refundEventId())
                .addValue("tenant", p.tenantId()).addValue("event", p.eventId()).addValue("amount", p.amount())
                .addValue("currency", p.currency()).addValue("credited", p.creditAlreadyPosted());
        jdbc.update("""
                INSERT INTO payment_refund_accounting(refund_event_id,tenant_id,event_id,amount,amount_currency,credit_already_posted)
                VALUES(:id,:tenant,:event,:amount,:currency,:credited) ON CONFLICT(refund_event_id) DO NOTHING
                """, args);
    }

    @Override
    public List<RefundAccounting.Posting> claim(Instant now) {
        return jdbc.query("""
                UPDATE payment_refund_accounting SET locked_until=:lease
                WHERE refund_event_id IN (SELECT refund_event_id FROM payment_refund_accounting
                  WHERE state='PENDING' AND next_attempt_at<=:now AND (locked_until IS NULL OR locked_until<:now)
                  ORDER BY next_attempt_at,refund_event_id LIMIT 25 FOR UPDATE SKIP LOCKED)
                RETURNING *
                """, Map.of("now", Timestamp.from(now), "lease", Timestamp.from(now.plusSeconds(LEASE_SECONDS))),
                (rs, n) -> new RefundAccounting.Posting(rs.getString("refund_event_id"), rs.getString("tenant_id"),
                        rs.getString("event_id"), rs.getBigDecimal("amount"), rs.getString("amount_currency"),
                        rs.getBoolean("credit_already_posted"), rs.getInt("attempts")));
    }

    @Override
    public void delivered(RefundAccounting.Posting p) {
        jdbc.update("UPDATE payment_refund_accounting SET state='DONE',locked_until=NULL,last_error=NULL WHERE refund_event_id=:id",
                Map.of("id", p.refundEventId()));
    }

    @Override
    public void retry(RefundAccounting.Posting p, Instant nextAttempt, String reason) {
        jdbc.update("""
                UPDATE payment_refund_accounting SET attempts=attempts+1,next_attempt_at=:next,locked_until=NULL,last_error=:reason
                WHERE refund_event_id=:id AND state='PENDING' AND attempts=:attempts
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", p.refundEventId())
                .addValue("attempts", p.attempts()).addValue("next", Timestamp.from(nextAttempt))
                .addValue("reason", reason == null ? null : reason.substring(0, Math.min(MAX_ERROR_LENGTH, reason.length()))));
    }
}
