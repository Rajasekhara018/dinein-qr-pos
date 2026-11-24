package com.heuristq.dinein.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientMaskTest {

    @Test
    void masksEmailLocalPart() {
        assertThat(RecipientMask.email("rajesh@example.com")).isEqualTo("ra***@example.com");
        assertThat(RecipientMask.email("a@example.com")).isEqualTo("a***@example.com");
        assertThat(RecipientMask.email("not-an-email")).isEqualTo("***");
    }

    @Test
    void keepsOnlyLastFourPhoneDigits() {
        assertThat(RecipientMask.phone("+919876543210")).isEqualTo("******3210");
        assertThat(RecipientMask.phone("9876543210")).isEqualTo("******3210").doesNotContain("98765");
        assertThat(RecipientMask.phone("123")).isEqualTo("****");
    }

    @Test
    void truncatesTokens() {
        String token = "dGhpcyBpcyBhIHZlcnkgbG9uZyBmY20gcmVnaXN0cmF0aW9uIHRva2Vu";

        assertThat(RecipientMask.token(token)).isEqualTo("dGhpcy...");
        assertThat(RecipientMask.token("short")).isEqualTo("***");
    }

    @Test
    void handlesMissingValuesAndChannels() {
        assertThat(RecipientMask.email(null)).isNull();
        assertThat(RecipientMask.of(NotificationChannel.SMS, "9876543210")).isEqualTo("******3210");
        assertThat(RecipientMask.of(NotificationChannel.IN_APP, "OWNER")).isEqualTo("OWNER");
    }
}
