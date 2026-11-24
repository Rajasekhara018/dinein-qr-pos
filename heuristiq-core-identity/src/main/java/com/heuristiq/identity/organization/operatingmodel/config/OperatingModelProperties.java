package com.heuristiq.uam.organization.operatingmodel.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the operating model ({@code railcab.uam.operating-model.*}). Validated at startup: a missing or unknown zone
 * stops the service instead of silently using another day.
 *
 * @param defaultTimeZone time zone of an organization that has none of its own, deciding which day "today" is
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.operating-model")
public record OperatingModelProperties(@NotBlank String defaultTimeZone) {
}
