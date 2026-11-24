package com.railcab.nms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.railcab.nms.domain.model.DefaultNotificationTemplates.Seed;

class RideLifecycleTemplatesTest {

    private static final Set<NotificationEvent> RIDE_EVENTS = Set.of(
            NotificationEvent.BOOKING_CONFIRMED, NotificationEvent.BOOKING_CANCELLED, NotificationEvent.BOOKING_MODIFIED,
            NotificationEvent.DRIVER_OFFERED, NotificationEvent.DRIVER_ASSIGNED, NotificationEvent.DRIVER_ARRIVING,
            NotificationEvent.DRIVER_ARRIVED, NotificationEvent.RIDE_STARTED, NotificationEvent.RIDE_COMPLETED,
            NotificationEvent.TRIP_REMINDER, NotificationEvent.PAYMENT_LINK_SENT, NotificationEvent.REFUND_PROCESSED,
            NotificationEvent.PROMO_ALERT, NotificationEvent.SYSTEM_ALERT, NotificationEvent.ADMIN_BROADCAST);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static Set<String> events(NotificationChannel channel) {
        return RideLifecycleTemplates.seeds().stream().filter(s -> s.channel() == channel).map(Seed::event).collect(Collectors.toSet());
    }

    @Test
    void everyRideEventHasAnInAppAndAPushTemplate() {
        Set<String> expected = RIDE_EVENTS.stream().map(Enum::name).collect(Collectors.toSet());

        assertThat(events(NotificationChannel.IN_APP)).isEqualTo(expected);
        assertThat(events(NotificationChannel.FCM_PUSH)).isEqualTo(expected);
    }

    @Test
    void onlyBookingConfirmedAndDriverArrivedGetAnSms() {
        assertThat(events(NotificationChannel.SMS)).containsExactlyInAnyOrder("BOOKING_CONFIRMED", "DRIVER_ARRIVED");
        assertThat(RideLifecycleTemplates.seeds()).hasSize(RIDE_EVENTS.size() * 2 + 2);
    }

    @Test
    void everyTextUsesOnlyThePlaceholdersItsEventDeclares() {
        for (Seed seed : RideLifecycleTemplates.seeds()) {
            Set<String> allowed = EventAttributesConfig.ATTRIBUTES.getOrDefault(seed.event(), List.of()).stream()
                    .map(attribute -> attribute.replace("{", "").replace("}", "")).collect(Collectors.toSet());
            Matcher used = PLACEHOLDER.matcher((seed.subject() == null ? "" : seed.subject()) + " " + seed.body());
            while (used.find()) {
                assertThat(allowed).as(seed.event() + " " + seed.channel() + " uses {{" + used.group(1) + "}}").contains(used.group(1));
            }
        }
    }

    @Test
    void theEventsThatDeclareNoPlaceholdersCarryFixedWording() {
        for (Seed seed : RideLifecycleTemplates.seeds()) {
            if (!EventAttributesConfig.ATTRIBUTES.containsKey(seed.event())) {
                assertThat(seed.body()).as(seed.event()).doesNotContain("{{");
                assertThat(seed.subject()).as(seed.event()).doesNotContain("{{");
            }
        }
    }

    @Test
    void noEventAndChannelIsSeededTwiceAcrossAllDefaults() {
        Set<String> seen = new HashSet<>();
        for (Seed seed : DefaultNotificationTemplates.DEFAULTS) {
            assertThat(seen.add(seed.event() + "/" + seed.channel())).as(seed.event() + "/" + seed.channel()).isTrue();
        }
        assertThat(DefaultNotificationTemplates.DEFAULTS).containsAll(RideLifecycleTemplates.seeds());
    }

    @Test
    void templateNamesAreUniqueAndEveryTextIsFilledIn() {
        List<Seed> seeds = RideLifecycleTemplates.seeds();

        assertThat(seeds.stream().map(Seed::name).collect(Collectors.toSet())).hasSameSizeAs(seeds);
        assertThat(seeds).allSatisfy(seed -> {
            assertThat(seed.body()).isNotBlank();
            if (seed.channel() != NotificationChannel.SMS) {
                assertThat(seed.subject()).isNotBlank();
            }
        });
    }

    @Test
    void anSmsStaysWithinOneMessageEvenWithLongValues() {
        for (Seed seed : RideLifecycleTemplates.seeds()) {
            if (seed.channel() != NotificationChannel.SMS) {
                continue;
            }
            String worstCase = PLACEHOLDER.matcher(seed.body().replace(DefaultNotificationTemplates.BRAND_NAME_TOKEN, "X".repeat(15)))
                    .replaceAll(match -> "x".repeat(25));
            assertThat(worstCase.length()).as(seed.event() + " SMS: " + worstCase).isLessThanOrEqualTo(160);
        }
    }

    @Test
    void theTextsMakeNoPromiseAboutArrivalTimeOrRefundTiming() {
        for (Seed seed : RideLifecycleTemplates.seeds()) {
            assertThat(seed.body().toLowerCase()).as(seed.event()).doesNotContain("{{eta}}").doesNotContain("within").doesNotContain("minutes")
                    .doesNotContain("business days").doesNotContain("guarantee");
        }
    }

    @Test
    void onlyAnSmsCarriesABrandTokenSoAnAdminEditNeverMatchesAnOldBrand() {
        for (Seed seed : RideLifecycleTemplates.seeds()) {
            if (seed.channel() != NotificationChannel.SMS) {
                // Branding uses %TOKEN_NAME%; a literal percentage (such as a promotion's discount) is valid text.
                assertThat(seed.body()).as(seed.event() + " " + seed.channel() + " body")
                        .doesNotContainPattern("%[A-Z][A-Z_]*%");
                assertThat(seed.subject()).as(seed.event() + " " + seed.channel() + " subject")
                        .doesNotContainPattern("%[A-Z][A-Z_]*%");
            }
        }
    }
}
