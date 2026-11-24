package com.heuristiq.paymentservice.salesettlement;

import com.heuristiq.paymentservice.salesettlement.domain.SaleEventIds;
import com.heuristiq.paymentservice.salesettlement.domain.SaleSettlementAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The payment -> wallet sale event id mapping (SaleSettlementConstants table). */
class SaleEventIdsTest {

    @Test
    void aRidePaymentPaysForTheRideCompletedSale() {
        assertThat(SaleEventIds.of(null, null, "r-1")).contains("ride-completed:r-1");
        assertThat(SaleEventIds.of(" ", "x", "r-2")).contains("ride-completed:r-2");
    }

    @Test
    void aMatrimonyPaymentPaysForItsTransactionsSale() {
        assertThat(SaleEventIds.of("matrimonial-api", "txn-9", null)).contains("MATRIMONY:SALE:txn-9");
    }

    @Test
    void aFoodPaymentPaysForTheDeliveredOrdersSale() {
        assertThat(SaleEventIds.of("food-delivery-platform", "o-1", null)).contains("food-order-delivered:o-1");
    }

    @Test
    void anythingElsePaysForNoKnownSale() {
        assertThat(SaleEventIds.of("train-service", "t-1", null)).isEmpty();
        assertThat(SaleEventIds.of("matrimonial-api", null, "r-1")).isEmpty();
        assertThat(SaleEventIds.of(null, null, null)).isEmpty();
    }

    @Test
    void keysFollowTheWalletContract() {
        assertThat(SaleEventIds.idempotencyKey(SaleSettlementAction.SETTLE, "ride-completed:r-1")).isEqualTo("sale-settle:ride-completed:r-1");
        assertThat(SaleEventIds.idempotencyKey(SaleSettlementAction.CANCEL, "MATRIMONY:SALE:t")).isEqualTo("sale-cancel:MATRIMONY:SALE:t");
    }
}
