package com.heuristq.dinein.order.domain;

import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, CONFIRMED",
            "PENDING_PAYMENT, EXPIRED",
            "PENDING_PAYMENT, PAYMENT_FAILED",
            "EXPIRED, CONFIRMED",
            "PAYMENT_FAILED, CONFIRMED",
            "CONFIRMED, PREPARING",
            "CONFIRMED, CANCELLED",
            "PREPARING, READY",
            "PREPARING, CANCELLED",
            "READY, COMPLETED",
            "READY, PREPARING",
            "PENDING_PAYMENT, CANCELLED"
    })
    void allowsLegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, PREPARING",
            "CONFIRMED, READY",
            "CONFIRMED, CONFIRMED",
            "READY, CANCELLED",
            "COMPLETED, CANCELLED",
            "CANCELLED, CONFIRMED",
            "EXPIRED, PREPARING"
    })
    void rejectsIllegalTransitionsWithConflict(OrderStatus from, OrderStatus to) {
        assertThat(OrderStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> OrderStateMachine.assertTransition(from, to))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("ILLEGAL_TRANSITION");
                });
    }

    @Test
    void terminalStatesHaveNoExits() {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(OrderStateMachine.canTransition(OrderStatus.COMPLETED, to)).isFalse();
            assertThat(OrderStateMachine.canTransition(OrderStatus.CANCELLED, to)).isFalse();
        }
    }
}
