package com.heuristiq.paymentservice.payment.lifecycle;

import com.heuristiq.paymentservice.payment.PaymentConstants;
import com.heuristiq.paymentservice.payment.model.Payment;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Records a row in {@code payment_status_transitions} whenever a payment is created or its status
 * changes, no matter which code path changed it (webhook, status poll, cash capture, settlement,
 * refund, ...) -- so none of those paths has to remember to log history.
 *
 * <p>The previous status is read back from the history itself (the last recorded {@code to_status})
 * rather than held on the entity, so a payment created before this table existed simply gets its
 * first row on its next change. Recording is best-effort: a failure is logged and swallowed, history
 * must never be able to fail a payment.
 */
@Slf4j
@Component
public class PaymentLifecycleListener {

    private final JdbcTemplate jdbc;

    @Autowired
    public PaymentLifecycleListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostPersist
    public void onCreate(Payment payment) {
        record(payment, null);
    }

    @PostUpdate
    public void onUpdate(Payment payment) {
        List<String> last = jdbc.queryForList(
                "SELECT to_status FROM payment_status_transitions WHERE payment_id = ? ORDER BY id DESC LIMIT 1",
                String.class, payment.getInternalPaymentId());
        String previous = last.isEmpty() ? null : last.get(0);
        String current = payment.getStatus() == null ? null : payment.getStatus().name();
        if (current != null && !current.equals(previous)) {
            record(payment, previous);
        }
    }

    private void record(Payment payment, String fromStatus) {
        if (payment.getStatus() == null) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO payment_status_transitions (payment_id, from_status, to_status, source, reason, transitioned_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    payment.getInternalPaymentId(), fromStatus, payment.getStatus().name(),
                    sourceFor(payment), reasonFor(payment),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Could not record the status change of payment {}: {}", payment.getInternalPaymentId(), e.getMessage());
        }
    }

    /** The explicit source if one was set for this thread, otherwise a best guess from the new status. */
    private static String sourceFor(Payment payment) {
        String explicit = PaymentLifecycleContext.currentSource();
        if (explicit != null) {
            return explicit;
        }
        return switch (payment.getStatus()) {
            case INITIATED -> "CHECKOUT";
            case SETTLED -> "SETTLEMENT";
            case REFUNDED, PARTIALLY_REFUNDED -> "REFUND";
            default -> "PAYMENT_SERVICE";
        };
    }

    private static String reasonFor(Payment payment) {
        String failure = payment.getFailureReason();
        if (failure == null || failure.isBlank()) {
            return null;
        }
        return failure.length() > PaymentConstants.MAX_FAILURE_REASON ? failure.substring(0, PaymentConstants.MAX_FAILURE_REASON) : failure;
    }
}
