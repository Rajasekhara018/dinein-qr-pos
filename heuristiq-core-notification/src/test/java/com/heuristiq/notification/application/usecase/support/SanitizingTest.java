package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.NotificationOrigin;
import com.heuristiq.notification.application.dto.NotificationRequest;

/** ErrorSanitizer, RecipientMasker and NotificationOrigin: what may leave NMS and in what shape. */
class SanitizingTest {

    @Test
    void errorSanitizerRemovesCredentialsAndQueryStrings() {
        String raw = "Connection refused: https://sms.example.com/bulksms?username=bob&password=s3cret&dest=9876543210 "
                + "Authorization: Bearer abc.def.ghi token=XYZ";

        String clean = ErrorSanitizer.sanitize(raw);

        assertThat(clean).doesNotContain("s3cret").doesNotContain("bob").doesNotContain("abc.def.ghi")
                .doesNotContain("XYZ").doesNotContain("9876543210");
    }

    @Test
    void errorSanitizerBoundsLengthAndHandlesEmpty() {
        assertThat(ErrorSanitizer.sanitize("x".repeat(5000))).hasSize(ErrorSanitizer.MAX_LENGTH);
        assertThat(ErrorSanitizer.sanitize(null)).isNull();
        assertThat(ErrorSanitizer.sanitize("   ")).isNull();
        assertThat(ErrorSanitizer.sanitize("Provider returned false")).isEqualTo("Provider returned false");
    }

    @Test
    void recipientMaskerNeverExposesAFullRecipient() {
        assertThat(RecipientMasker.mask("9876543210")).isEqualTo("******3210");
        assertThat(RecipientMasker.mask("jane@example.com")).isEqualTo("j***@example.com");
        assertThat(RecipientMasker.mask("1234")).isEqualTo("***");
        assertThat(RecipientMasker.mask("fcm-token-abcdefghijklmnopqrstuvwxyz0123456789"))
                .endsWith("6789").doesNotContain("abcdefghij");
        assertThat(RecipientMasker.mask(null)).isNull();
    }

    @Test
    void originIsOptionalAndDefaultsTheEventCode() {
        NotificationRequest request = new NotificationRequest();
        request.setEvent("RIDE_BOOKED");

        NotificationOrigin origin = NotificationOrigin.from(request);

        assertThat(origin.sourceService()).isNull();
        assertThat(origin.triggeringUserId()).isNull();
        assertThat(origin.entityType()).isNull();
        assertThat(origin.entityId()).isNull();
        assertThat(origin.eventCode()).isEqualTo("RIDE_BOOKED");
    }

    @Test
    void originIsTrimmedAndBounded() {
        NotificationRequest request = new NotificationRequest();
        request.setEvent("E");
        request.setSourceService("  rider-payment ");
        request.setEventCode("PAYMENT_CAPTURED");
        request.setTriggeringUserId("u".repeat(500));
        request.setEntityType("BOOKING");
        request.setEntityId("b-1");

        NotificationOrigin origin = NotificationOrigin.from(request);

        assertThat(origin.sourceService()).isEqualTo("rider-payment");
        assertThat(origin.eventCode()).isEqualTo("PAYMENT_CAPTURED");
        assertThat(origin.triggeringUserId()).hasSize(128);
        assertThat(origin.entityType()).isEqualTo("BOOKING");
        assertThat(origin.entityId()).isEqualTo("b-1");
    }
}
