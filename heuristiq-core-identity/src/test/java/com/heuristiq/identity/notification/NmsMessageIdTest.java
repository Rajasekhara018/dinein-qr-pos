package com.heuristiq.identity.notification;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** NMS drops a repeated message id as a duplicate -- an OTP's id must differ per OTP. */
class NmsMessageIdTest {

    private static Map<String, String> otpVars(String otp) {
        return Map.of("otp", otp, "passengerName", "Nagi Reddy ", "purpose", "LOGIN", "expiryMinutes", "5");
    }

    @Test
    void differentOtpsForTheSamePersonGetDifferentIds() {
        List<String> to = List.of("nagi@example.com");
        String first = NmsNotificationService.messageId("OTP_REQUEST", to, null, otpVars("123456"), 100);
        String second = NmsNotificationService.messageId("OTP_REQUEST", to, null, otpVars("654321"), 100);
        assertNotEquals(first, second);
    }

    @Test
    void aRetryOfTheSameOtpWithinTheMinuteKeepsTheSameId() {
        List<String> to = List.of("nagi@example.com");
        assertEquals(
                NmsNotificationService.messageId("OTP_REQUEST", to, null, otpVars("123456"), 100),
                NmsNotificationService.messageId("OTP_REQUEST", to, null, otpVars("123456"), 100));
    }

    @Test
    void thePlainOtpIsNeverPartOfTheId() {
        String id = NmsNotificationService.messageId("OTP_REQUEST", null, "6281298823", otpVars("123456"), 100);
        assertFalse(id.contains("123456"));
    }

    @Test
    void nonOtpEventsKeepTheirPerAccountId() {
        Map<String, String> vars = Map.of("email", "nagi@example.com", "passengerName", "Nagi");
        assertEquals("uam:ACCOUNT_CREATED:nagi@example.com",
                NmsNotificationService.messageId("ACCOUNT_CREATED", List.of("nagi@example.com"), null, vars, 100));
    }

    @Test
    void theMessageIdIsPerPersonAndOrganization() {
        List<String> to = List.of("asha@acme.com");
        String acme = NmsNotificationService.messageId("ORGANIZATION_ADMIN_WELCOME", to, null,
                Map.of("email", "asha@acme.com", "organizationCode", "ACME"), 1);
        String globex = NmsNotificationService.messageId("ORGANIZATION_ADMIN_WELCOME", to, null,
                Map.of("email", "asha@acme.com", "organizationCode", "GLOBEX"), 1);

        org.assertj.core.api.Assertions.assertThat(acme).isNotEqualTo(globex);
    }
}
