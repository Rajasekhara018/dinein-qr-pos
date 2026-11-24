package com.heuristiq.uam.biometric.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of "sign in with fingerprint / Face ID" ({@code railcab.uam.biometric.*}). Validated at startup.
 *
 * @param reauthWindow      how recent the caller's interactive sign-in (password, one-time code, Google; not a refresh and
 *                          not a biometric sign-in) must be to enrol a device without typing the current password
 * @param challengeTtl      how long a sign-in challenge may be answered
 * @param inactivityExpiry  an enrolled device not used for this long expires and must be enrolled again
 * @param maxFailedAttempts failed biometric sign-ins allowed per installation within {@code attemptWindow}; further ones are
 *                          refused with 429 TOO_MANY_ATTEMPTS until the window ends
 * @param attemptWindow     length of that window, counted from the first failure
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.biometric")
public record BiometricProperties(
        @DefaultValue("5m") @NotNull Duration reauthWindow,
        @DefaultValue("60s") @NotNull Duration challengeTtl,
        @DefaultValue("90d") @NotNull Duration inactivityExpiry,
        @DefaultValue("5") @Min(1) @Max(100) int maxFailedAttempts,
        @DefaultValue("15m") @NotNull Duration attemptWindow) {
}
