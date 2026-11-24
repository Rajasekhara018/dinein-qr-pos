package com.railcab.nms.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards that the SOS events exist and expose exactly the placeholders Rider_RideService sends. */
class EventAttributesConfigTest {

    @Test
    void sosEventsHaveTheirPlaceholders() {
        assertThat(EventAttributesConfig.forEvent(NotificationEvent.SOS_ALERT_RAISED.name()))
                .containsExactly("{{alertId}}", "{{rideId}}", "{{userId}}", "{{lat}}", "{{lng}}");
        assertThat(EventAttributesConfig.forEvent(NotificationEvent.SOS_ALERT_ESCALATED.name()))
                .containsExactly("{{alertId}}", "{{rideId}}", "{{userId}}", "{{lat}}", "{{lng}}");
        assertThat(EventAttributesConfig.forEvent(NotificationEvent.SOS_CONTACT_ALERT.name()))
                .containsExactly("{{alertId}}", "{{rideId}}", "{{contactName}}", "{{lat}}", "{{lng}}");
    }

    @Test
    void everyNewSosEventHasNonEmptyAttributes() {
        for (NotificationEvent event : List.of(NotificationEvent.SOS_ALERT_RAISED,
                NotificationEvent.SOS_ALERT_ESCALATED, NotificationEvent.SOS_CONTACT_ALERT)) {
            assertThat(EventAttributesConfig.forEvent(event.name())).as(event.name()).isNotEmpty();
        }
    }

    @Test
    void unknownEventYieldsEmptyListNotNull() {
        assertThat(EventAttributesConfig.forEvent("NOT_AN_EVENT")).isEmpty();
    }
}
