package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.order.dto.OrderDtos.StaffPaymentMethod;
import com.heuristq.dinein.payment.domain.OfflinePaymentMethod;
import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTypeRulesTest {

    @Test
    void missingTypeDefaultsToDineIn() {
        assertThat(OrderTypeRules.resolve(null, false)).isEqualTo(OrderType.DINE_IN);
        assertThat(OrderTypeRules.resolve(null, true)).isEqualTo(OrderType.DINE_IN);
    }

    @Test
    void takeawayIsAllowedWhenEnabled() {
        assertThat(OrderTypeRules.resolve(OrderType.TAKEAWAY, true)).isEqualTo(OrderType.TAKEAWAY);
    }

    @Test
    void takeawayIsRejectedWhenDisabled() {
        assertThatThrownBy(() -> OrderTypeRules.resolve(OrderType.TAKEAWAY, false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getCode()).isEqualTo("TAKEAWAY_DISABLED");
                });
    }

    @Test
    void dineInNeedsATableButTakeawayDoesNot() {
        assertThatThrownBy(() -> OrderTypeRules.requireTableForDineIn(OrderType.DINE_IN, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("TABLE_REQUIRED");
        assertThatCode(() -> OrderTypeRules.requireTableForDineIn(OrderType.DINE_IN, 7L)).doesNotThrowAnyException();
        assertThatCode(() -> OrderTypeRules.requireTableForDineIn(OrderType.TAKEAWAY, null)).doesNotThrowAnyException();
    }

    @Test
    void staffPaymentMethodsMapToOfflineMethodsExceptOnline() {
        assertThat(StaffPaymentMethod.ONLINE.offline()).isNull();
        assertThat(StaffPaymentMethod.CASH.offline()).isEqualTo(OfflinePaymentMethod.CASH);
        assertThat(StaffPaymentMethod.UPI_AT_COUNTER.offline()).isEqualTo(OfflinePaymentMethod.UPI_AT_COUNTER);
        assertThat(StaffPaymentMethod.CARD_AT_COUNTER.offline()).isEqualTo(OfflinePaymentMethod.CARD_AT_COUNTER);
    }
}
