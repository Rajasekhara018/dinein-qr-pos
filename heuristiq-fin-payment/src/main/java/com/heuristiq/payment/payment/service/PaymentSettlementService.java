package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.config.SettlementProperties;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStateMachine;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bookkeeps the CAPTURED -> SETTLED transition (HLD §17.2) for payments old enough that a real
 * gateway settlement cycle would realistically have paid out by now — see SettlementScheduler's
 * javadoc for why this is an internal state transition only, not a call to any external
 * "settlement API". Invoked by SettlementScheduler on a fixed-rate ShedLock-guarded schedule.
 * Extracted out of PaymentService so payment creation/retrieval and this settlement job aren't
 * one class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSettlementService {

    private final PaymentRepository paymentRepository;
    private final SettlementProperties settlementProperties;

    @Transactional
    public void settleOldCapturedPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(settlementProperties.getDelayHours());
        List<Payment> due = paymentRepository.findByStatusAndUpdatedAtBefore(PaymentStatus.CAPTURED, threshold);
        LocalDateTime now = LocalDateTime.now();
        for (Payment payment : due) {
            if (!PaymentStateMachine.apply(payment, PaymentStatus.SETTLED)) {
                continue;
            }
            payment.setSettledAt(now);
            paymentRepository.save(payment);
        }
        log.info("Settlement job: transitioned {} CAPTURED payment(s) older than {}h (updatedAt < {}) to SETTLED",
                due.size(), settlementProperties.getDelayHours(), threshold);
    }
}
