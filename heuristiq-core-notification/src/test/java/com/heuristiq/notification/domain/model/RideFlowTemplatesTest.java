package com.heuristiq.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.heuristiq.notification.domain.model.DefaultNotificationTemplates.Seed;

/** The ride flow events of docs/RIDE_FLOW_FIXES_2026-11.md section 3 exist with their variables and default texts. */
class RideFlowTemplatesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final List<NotificationEvent> PUSH_EVENTS = List.of(
            NotificationEvent.DRIVER_NEARBY, NotificationEvent.WAITING_CHARGE_STARTED, NotificationEvent.PAYMENT_PENDING,
            NotificationEvent.PAYMENT_COMPLETED, NotificationEvent.RIDE_CLOSED, NotificationEvent.RIDE_DISPUTE_OPENED,
            NotificationEvent.RIDE_DISPUTE_RESOLVED);

    private static Set<NotificationChannel> channels(String event) {
        return DefaultNotificationTemplates.DEFAULTS.stream().filter(s -> s.event().equals(event)).map(Seed::channel)
                .collect(Collectors.toSet());
    }

    @Test
    void theEventNamesMatchTheContract() {
        assertThat(PUSH_EVENTS).extracting(Enum::name).containsExactly("DRIVER_NEARBY", "WAITING_CHARGE_STARTED",
                "PAYMENT_PENDING", "PAYMENT_COMPLETED", "RIDE_CLOSED", "RIDE_DISPUTE_OPENED", "RIDE_DISPUTE_RESOLVED");
        assertThat(NotificationEvent.valueOf("EARLY_DROP_CONFIRM_CALL")).isNotNull();
    }

    @Test
    void eachPushEventShipsInAppAndPushOnly() {
        for (NotificationEvent event : PUSH_EVENTS) {
            assertThat(channels(event.name())).as(event.name())
                    .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.FCM_PUSH);
        }
    }

    @Test
    void theConfirmationCallIsVoiceOnlyAndTheEarlyDropRequestGetsAnSms() {
        assertThat(channels("EARLY_DROP_CONFIRM_CALL")).containsExactly(NotificationChannel.VOICE);
        assertThat(channels("EARLY_DROP_REQUESTED")).contains(NotificationChannel.SMS);
    }

    @Test
    void theVariablesAreTheContractOnes() {
        assertThat(EventAttributesConfig.forEvent("DRIVER_NEARBY"))
                .containsExactly("{{rideId}}", "{{distanceMeters}}", "{{driverName}}", "{{vehicleNumber}}");
        assertThat(EventAttributesConfig.forEvent("WAITING_CHARGE_STARTED")).containsExactly("{{rideId}}", "{{waitingChargePerMin}}");
        assertThat(EventAttributesConfig.forEvent("PAYMENT_PENDING"))
                .containsExactly("{{rideId}}", "{{finalFare}}", "{{paymentMethod}}", "{{waitingCharge}}");
        assertThat(EventAttributesConfig.forEvent("PAYMENT_COMPLETED")).containsExactly("{{rideId}}", "{{finalFare}}", "{{paymentMethod}}");
        assertThat(EventAttributesConfig.forEvent("RIDE_CLOSED")).containsExactly("{{rideId}}", "{{finalFare}}");
        assertThat(EventAttributesConfig.forEvent("EARLY_DROP_CONFIRM_CALL")).containsExactly("{{rideId}}", "{{finalFare}}", "{{dropAddress}}");
        assertThat(EventAttributesConfig.forEvent("RIDE_DISPUTE_OPENED")).containsExactly("{{rideId}}", "{{reason}}");
        assertThat(EventAttributesConfig.forEvent("RIDE_DISPUTE_RESOLVED")).containsExactly("{{rideId}}", "{{resolution}}", "{{finalFare}}");
        assertThat(EventAttributesConfig.forEvent("DRIVER_ARRIVED"))
                .contains("{{arrivedAtPickupAt}}", "{{freeWaitingMinutes}}", "{{waitingChargePerMin}}");
        assertThat(EventAttributesConfig.forEvent("RIDE_STARTED")).contains("{{waitingMinutes}}", "{{waitingCharge}}");
        assertThat(EventAttributesConfig.forEvent("EARLY_DROP_REQUESTED")).contains("{{earlyDropExpiresAt}}");
    }

    @Test
    void everyTextUsesOnlyItsEventsPlaceholdersAndIsFilledIn() {
        for (Seed seed : RideFlowTemplates.seeds()) {
            Set<String> allowed = EventAttributesConfig.forEvent(seed.event()).stream()
                    .map(a -> a.replace("{", "").replace("}", "")).collect(Collectors.toSet());
            assertThat(seed.body()).as(seed.name()).isNotBlank();
            if (seed.channel() == NotificationChannel.IN_APP || seed.channel() == NotificationChannel.FCM_PUSH) {
                assertThat(seed.subject()).as(seed.name()).isNotBlank();
            }
            Matcher used = PLACEHOLDER.matcher((seed.subject() == null ? "" : seed.subject()) + " " + seed.body());
            while (used.find()) {
                assertThat(allowed).as(seed.name() + " uses {{" + used.group(1) + "}}").contains(used.group(1));
            }
        }
    }

    @Test
    void faresShowTheRupeeSignAndTheSmsStaysOneGsmMessage() {
        Map<String, Seed> byName = RideFlowTemplates.seeds().stream().collect(Collectors.toMap(Seed::name, s -> s));
        assertThat(byName.get("Payment Pending - Push").body()).contains("₹{{finalFare}}");
        Seed sms = byName.get("Early Drop Requested - SMS");
        assertThat(sms.body()).startsWith(DefaultNotificationTemplates.BRAND_NAME_TOKEN);
        String worstCase = PLACEHOLDER.matcher(sms.body().replace(DefaultNotificationTemplates.BRAND_NAME_TOKEN, "X".repeat(15)))
                .replaceAll(m -> "x".repeat(10));
        assertThat(worstCase.length()).isLessThanOrEqualTo(160);
        assertThat(worstCase.chars().allMatch(c -> c < 128)).isTrue();
    }

    @Test
    void theDisputeReasonCodeIsNeverShownToTheCustomer() {
        for (Seed seed : RideFlowTemplates.seeds()) {
            assertThat(seed.body()).doesNotContain("{{reason}}").doesNotContain("{{resolution}}");
        }
    }

    @Test
    void voiceIsAChannelAndTheNewSeedsAreInTheDefaults() {
        assertThat(NotificationChannel.valueOf("VOICE")).isEqualTo(NotificationChannel.VOICE);
        assertThat(DefaultNotificationTemplates.DEFAULTS).containsAll(RideFlowTemplates.seeds());
        // The contract events, NO_DRIVER_FOUND (push + in-app each), the early drop SMS and the voice call.
        assertThat(RideFlowTemplates.seeds()).hasSize((PUSH_EVENTS.size() + 1) * 2 + 2);
    }

    @Test
    void noDriverFoundIsAPushAndInAppMessageAboutRetrying() {
        assertThat(channels("NO_DRIVER_FOUND")).containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.FCM_PUSH);
        assertThat(EventAttributesConfig.forEvent("NO_DRIVER_FOUND")).containsExactly("{{rideId}}", "{{searchTimeoutSeconds}}");
        Seed push = RideFlowTemplates.seeds().stream().filter(s -> s.name().equals("No Driver Found - Push")).findFirst().orElseThrow();
        assertThat(push.subject()).isEqualTo("Unable to book your ride");
        assertThat(push.body()).contains("try again");
    }
}
