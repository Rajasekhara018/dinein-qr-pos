package com.heuristiq.paymentservice.payment.service;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.config.SettlementProperties;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentSettlementService — extracted out of PaymentService (see that class's javadoc).
 * HLD §17.2's CAPTURED -> SETTLED transition, driven by SettlementScheduler ->
 * PaymentSettlementService.settleOldCapturedPayments(). Proves the threshold logic: a CAPTURED
 * payment whose updatedAt is older than app.settlement.delay-hours is transitioned to SETTLED
 * with settledAt stamped; a CAPTURED payment updated more recently than the threshold is left
 * alone (PaymentRepository.findByStatusAndUpdatedAtBefore itself excludes it, so this test proves
 * the method only saves what the repository query hands it, and stamps the right fields).
 */
class PaymentSettlementServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentSettlementService paymentSettlementService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        SettlementProperties settlementProperties = new SettlementProperties();
        settlementProperties.setDelayHours(24);

        paymentSettlementService = new PaymentSettlementService(paymentRepository, settlementProperties);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void settlesOldEnoughCapturedPayment() {
        Payment oldPayment = Payment.builder()
                .internalPaymentId("pay-old")
                .rideId("ride-1")
                .customerId("cust-1")
                .amount(new BigDecimal("100.0"))
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.CAPTURED)
                .updatedAt(LocalDateTime.now().minusHours(48))
                .build();
        when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.CAPTURED), any(LocalDateTime.class)))
                .thenReturn(List.of(oldPayment));

        paymentSettlementService.settleOldCapturedPayments();

        assertThat(oldPayment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(oldPayment.getSettledAt()).isNotNull();
        verify(paymentRepository, times(1)).save(oldPayment);
    }

    @Test
    void leavesRecentCapturedPaymentsAlone() {
        // The repository query itself (findByStatusAndUpdatedAtBefore) is what excludes recent
        // payments — simulate that by returning an empty list, and prove the service does not
        // save anything when there's nothing due.
        when(paymentRepository.findByStatusAndUpdatedAtBefore(eq(PaymentStatus.CAPTURED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        paymentSettlementService.settleOldCapturedPayments();

        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
