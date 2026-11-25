package com.heuristiq.identity.time;

import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

/**
 * The IANA zone in which this service wrote its zone-less (wall-clock) timestamps. An unknown id fails the binding,
 * so a typo stops the start instead of being silently ignored. Not used by business logic yet.
 *
 * @param zone zone id from {@code railcab.time.legacy-zone}; when unset the service default
 *             ({@link LegacyZoneConstants#DEFAULT_ZONE_ID}) applies
 */
@Validated
@ConfigurationProperties(prefix = LegacyZoneConstants.PROPERTY_PREFIX)
public record LegacyZoneProperties(@Name(LegacyZoneConstants.PROPERTY_NAME) @NotNull ZoneId zone) {

    public LegacyZoneProperties {
        if (zone == null) {
            zone = ZoneId.of(LegacyZoneConstants.DEFAULT_ZONE_ID);
        }
    }
}
