package com.heuristiq.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

    @Test
    void legacyValuesStillExist() {
        assertThat(DeliveryStatus.valueOf("SENT")).isEqualTo(DeliveryStatus.SENT);
        assertThat(DeliveryStatus.valueOf("FAILED")).isEqualTo(DeliveryStatus.FAILED);
        assertThat(DeliveryStatus.valueOf("SKIPPED")).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void statusMovesForwardOnly() {
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.QUEUED, DeliveryStatus.SENT)).isTrue();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.DELIVERED)).isTrue();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.DELIVERED, DeliveryStatus.READ)).isTrue();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.FAILED)).isTrue();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.BOUNCED)).isTrue();
    }

    @Test
    void duplicatesAndLateArrivalsAreRejected() {
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.SENT)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.DELIVERED, DeliveryStatus.SENT)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.READ, DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.FAILED, DeliveryStatus.BOUNCED)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.QUEUED)).isFalse();
    }

    @Test
    void skippedNeverChangesAndNeverBecomes() {
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SKIPPED, DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, DeliveryStatus.SKIPPED)).isFalse();
        assertThat(DeliveryStatus.canAdvance(DeliveryStatus.SENT, null)).isFalse();
    }

    @Test
    void aLegacyRowWithoutStatusAcceptsAReceipt() {
        assertThat(DeliveryStatus.canAdvance(null, DeliveryStatus.DELIVERED)).isTrue();
    }

    @Test
    void classifiesOutcomes() {
        assertThat(DeliveryStatus.DELIVERED.isDelivered()).isTrue();
        assertThat(DeliveryStatus.READ.isDelivered()).isTrue();
        assertThat(DeliveryStatus.SENT.isDelivered()).isFalse();
        assertThat(DeliveryStatus.FAILED.isFailure()).isTrue();
        assertThat(DeliveryStatus.BOUNCED.isFailure()).isTrue();
        assertThat(DeliveryStatus.DELIVERED.isFailure()).isFalse();
    }
}
