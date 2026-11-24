package com.heuristiq.payment.payment.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStateMachineTest {

    @Test
    void capturedPayment_cannotBeMovedToFailedByALateEvent() {
        Payment payment = Payment.builder().internalPaymentId("p1").status(PaymentStatus.CAPTURED).build();

        assertThat(PaymentStateMachine.apply(payment, PaymentStatus.FAILED)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void settledAndRefundedPayments_cannotGoBackwards() {
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.SETTLED, PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.SETTLED, PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.CAPTURED)).isFalse();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED)).isFalse();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.AMOUNT_MISMATCH, PaymentStatus.CAPTURED)).isFalse();
    }

    @Test
    void sameStatus_isNotATransition() {
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.CAPTURED, PaymentStatus.CAPTURED)).isFalse();
    }

    @Test
    void legalForwardMoves_areAllowed() {
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.INITIATED, PaymentStatus.CAPTURED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.INITIATED, PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.CAPTURED, PaymentStatus.SETTLED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)).isTrue();
    }

    @Test
    void failedOrExpiredPayment_canStillBeCapturedWhenTheGatewayReceivedTheMoney() {
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.FAILED, PaymentStatus.CAPTURED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.EXPIRED, PaymentStatus.CAPTURED)).isTrue();
    }

    @Test
    void illegalMove_leavesPaymentAndTimestampUntouched() {
        Payment payment = Payment.builder().internalPaymentId("p1").status(PaymentStatus.REFUNDED).build();

        PaymentStateMachine.apply(payment, PaymentStatus.CAPTURED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getUpdatedAt()).isNull();
    }
}
