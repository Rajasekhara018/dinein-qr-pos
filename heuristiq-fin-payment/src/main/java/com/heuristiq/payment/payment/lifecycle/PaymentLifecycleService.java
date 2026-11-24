package com.heuristiq.payment.payment.lifecycle;

import com.heuristiq.payment.payment.dto.admin.AdminPayment;
import com.heuristiq.payment.payment.dto.admin.AdminPaymentTransition;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Reads a payment's recorded status history (see {@link PaymentLifecycleListener}). */
@Service
@RequiredArgsConstructor
public class PaymentLifecycleService {

    private final JdbcTemplate jdbc;

    /**
     * Oldest first. A payment that predates history recording has no rows (or only the rows since
     * the feature shipped), so what is missing is reconstructed from the payment's own timestamps
     * and marked {@code DERIVED}: creation, then -- if it has moved on -- its current status.
     */
    @Transactional(readOnly = true)
    public List<AdminPaymentTransition> history(Payment payment) {
        List<AdminPaymentTransition> recorded = jdbc.query(
                "SELECT from_status, to_status, source, reason, transitioned_at FROM payment_status_transitions "
                        + "WHERE payment_id = ? ORDER BY id",
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp("transitioned_at");
                    return new AdminPaymentTransition(rs.getString("from_status"), rs.getString("to_status"),
                            rs.getString("source"), rs.getString("reason"),
                            at == null ? null : AdminPayment.toInstant(at.toLocalDateTime()));
                },
                payment.getInternalPaymentId());

        List<AdminPaymentTransition> result = new ArrayList<>();
        // History that does not begin with INITIATED (nothing recorded yet, or recording started after
        // the payment was created): reconstruct the start.
        if (recorded.isEmpty() || !PaymentStatus.INITIATED.name().equals(recorded.get(0).toStatus())) {
            result.add(derived(null, PaymentStatus.INITIATED, payment.getCreatedAt()));
        }
        if (recorded.isEmpty() && payment.getStatus() != null && payment.getStatus() != PaymentStatus.INITIATED) {
            LocalDateTime when = payment.getStatus() == PaymentStatus.SETTLED && payment.getSettledAt() != null
                    ? payment.getSettledAt() : payment.getUpdatedAt();
            result.add(derived(PaymentStatus.INITIATED, payment.getStatus(), when));
        }
        result.addAll(recorded);
        return result;
    }

    private static AdminPaymentTransition derived(PaymentStatus from, PaymentStatus to, LocalDateTime at) {
        return new AdminPaymentTransition(from == null ? null : from.name(), to.name(), "DERIVED", null,
                AdminPayment.toInstant(at));
    }
}
