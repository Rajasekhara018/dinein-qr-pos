package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.domain.model.NotificationChannel;

/** The push data contract (docs/RIDE_FLOW_FIXES_2026-11.md section 2). */
class PushDataBuilderTest {

    private static NotificationRequest ride(Map<String, String> variables) {
        NotificationRequest request = new NotificationRequest();
        request.setMessageId("msg-1");
        request.setEvent(" driver_nearby ");
        request.setEventCode("DRIVER_NEARBY");
        request.setEntityType("RIDE");
        request.setEntityId("ride-42");
        request.setVariables(variables);
        return request;
    }

    @Test
    void aRidePushCarriesTypeEntityRideIdUrlAndVariables() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("distanceMeters", "850");
        vars.put("driverName", "Ravi");

        Map<String, String> data = PushDataBuilder.build(ride(vars));

        assertThat(data).containsEntry("type", "DRIVER_NEARBY")
                .containsEntry("eventCode", "DRIVER_NEARBY")
                .containsEntry("entityType", "RIDE")
                .containsEntry("entityId", "ride-42")
                .containsEntry("rideId", "ride-42")
                .containsEntry("url", "/notifications")
                .containsEntry("distanceMeters", "850")
                .containsEntry("driverName", "Ravi")
                .doesNotContainKey("messageId");
    }

    @Test
    void sensitiveVariablesAreNeverCopiedWhateverTheirCase() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("otp", "123456");
        vars.put("rideOTP", "9999");
        vars.put("newPassword", "x");
        vars.put("clientSecret", "x");
        vars.put("accessToken", "x");
        vars.put("PIN", "1234");
        vars.put("cardCvv", "123");
        vars.put("finalFare", "250.00");

        Map<String, String> data = PushDataBuilder.build(ride(vars));

        assertThat(data).doesNotContainKeys("otp", "rideOTP", "newPassword", "clientSecret", "accessToken", "PIN", "cardCvv")
                .containsEntry("finalFare", "250.00");
        assertThat(data.values()).doesNotContain("123456", "9999", "1234");
    }

    @Test
    void linksAndPersonalContactOrAccountDataAreNeverCopied() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("resetLink", "https://x/reset?t=abc");
        vars.put("signInDetails", "x");
        vars.put("ownerEmail", "a@b.co");
        vars.put("customerPhone", "+919999999999");
        vars.put("mobileNumber", "9999999999");
        vars.put("accountNumber", "1234");
        vars.put("cardLast4", "4242");
        vars.put("driverName", "Ravi");
        vars.put("waitingChargePerMin", "2.0000");
        vars.put("freeWaitingMinutes", "3");

        Map<String, String> data = PushDataBuilder.build(ride(vars));

        assertThat(data).doesNotContainKeys("resetLink", "signInDetails", "ownerEmail", "customerPhone", "mobileNumber",
                        "accountNumber", "cardLast4")
                .containsEntry("driverName", "Ravi")
                .containsEntry("waitingChargePerMin", "2.0000")
                .containsEntry("freeWaitingMinutes", "3");
    }

    @Test
    void rideIdComesFromVariablesWhenTheEntityIsNotARide() {
        NotificationRequest request = new NotificationRequest();
        request.setEvent("PAYMENT_COMPLETED");
        request.setEntityType("PAYMENT");
        request.setEntityId("pay-1");
        request.setVariables(Map.of("rideId", "ride-7"));

        assertThat(PushDataBuilder.build(request)).containsEntry("rideId", "ride-7").containsEntry("entityId", "pay-1");
    }

    @Test
    void noRideIdWithoutARideEntityOrVariableAndBlankOriginFieldsAreLeftOut() {
        NotificationRequest request = new NotificationRequest();
        request.setEvent("PROMO_ALERT");
        request.setEventCode(" ");

        Map<String, String> data = PushDataBuilder.build(request);

        assertThat(data).containsOnlyKeys("type", "url");
    }

    @Test
    void eachValueIsCappedAt512Characters() {
        Map<String, String> data = PushDataBuilder.build(ride(Map.of("note", "a".repeat(2000))));

        assertThat(data.get("note")).hasSize(PushDataConstants.MAX_VALUE_LENGTH);
    }

    @Test
    void theWholeMapStaysWithin3500BytesDroppingTheLastVariablesFirst() {
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            vars.put("v" + i, "x".repeat(500));
        }

        Map<String, String> data = PushDataBuilder.build(ride(vars));

        int bytes = data.entrySet().stream()
                .mapToInt(e -> e.getKey().getBytes(StandardCharsets.UTF_8).length + e.getValue().getBytes(StandardCharsets.UTF_8).length)
                .sum();
        assertThat(bytes).isLessThanOrEqualTo(PushDataConstants.MAX_TOTAL_BYTES);
        assertThat(data).containsKeys("type", "rideId", "url", "v0", "v1").doesNotContainKey("v9");
    }

    @Test
    void variablesNeverOverrideTheContractKeysAndNullsAreSkipped() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("type", "FAKE");
        vars.put("url", "https://evil.example");
        vars.put("empty", null);

        Map<String, String> data = PushDataBuilder.build(ride(vars));

        assertThat(data).containsEntry("type", "DRIVER_NEARBY").containsEntry("url", "/notifications").doesNotContainKey("empty");
    }

    @Test
    void voiceAlsoCarriesTheMessageIdButPushDoesNot() {
        NotificationRequest request = ride(Map.of());

        assertThat(PushDataBuilder.forChannel(NotificationChannel.VOICE, request)).containsEntry("messageId", "msg-1");
        assertThat(PushDataBuilder.forChannel(NotificationChannel.FCM_PUSH, request)).doesNotContainKey("messageId");
    }

    @Test
    void aMissingRequestStillGivesTheInboxUrl() {
        assertThat(PushDataBuilder.build(null)).containsExactly(Map.entry("url", "/notifications"));
        assertThat(PushDataBuilder.forChannel(NotificationChannel.VOICE, null)).containsOnlyKeys("url");
    }
}
