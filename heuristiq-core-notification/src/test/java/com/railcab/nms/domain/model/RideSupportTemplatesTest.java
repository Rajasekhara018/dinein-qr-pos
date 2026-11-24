package com.railcab.nms.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Early drop and help desk events ship ready-made as in-app and push messages and only use placeholders their event provides. */
class RideSupportTemplatesTest {
    private static final List<NotificationEvent> EVENTS = List.of(
            NotificationEvent.EARLY_DROP_REQUESTED, NotificationEvent.EARLY_DROP_ACCEPTED,
            NotificationEvent.EARLY_DROP_DECLINED, NotificationEvent.SUPPORT_TICKET_REPLY);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\w+}}");

    @Test
    void everyEventHasInAppAndPushContent() {
        for (NotificationEvent event : EVENTS) {
            Set<NotificationChannel> channels = DefaultNotificationTemplates.DEFAULTS.stream()
                    .filter(seed -> seed.event().equals(event.name()))
                    .map(DefaultNotificationTemplates.Seed::channel)
                    .collect(Collectors.toSet());
            // The early drop request also goes out by SMS (ride flow contract, docs/RIDE_FLOW_FIXES_2026-11.md section 3).
            if (event == NotificationEvent.EARLY_DROP_REQUESTED) {
                assertThat(channels).as(event.name())
                        .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.FCM_PUSH, NotificationChannel.SMS);
                continue;
            }
            assertThat(channels).as(event.name())
                    .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.FCM_PUSH);
        }
    }

    @Test
    void seedsOnlyUsePlaceholdersTheEventProvides() {
        for (NotificationEvent event : EVENTS) {
            List<String> allowed = EventAttributesConfig.forEvent(event.name());
            assertThat(allowed).as(event.name()).isNotEmpty();
            for (DefaultNotificationTemplates.Seed seed : DefaultNotificationTemplates.DEFAULTS) {
                if (!seed.event().equals(event.name())) {
                    continue;
                }
                Matcher matcher = PLACEHOLDER.matcher(seed.body() + " " + seed.subject());
                while (matcher.find()) {
                    assertThat(allowed).as(seed.name()).contains(matcher.group());
                }
            }
        }
    }

    @Test
    void theEventCodesMatchWhatTheSendingServicesPost() {
        // Rider_RideService (TripConstants.NMS_EARLY_DROP_*) and Rider_AdminAPI (HelpdeskConstants.REPLY_EVENT) send these names.
        assertThat(EVENTS).extracting(Enum::name).containsExactly(
                "EARLY_DROP_REQUESTED", "EARLY_DROP_ACCEPTED", "EARLY_DROP_DECLINED", "SUPPORT_TICKET_REPLY");
    }
}
