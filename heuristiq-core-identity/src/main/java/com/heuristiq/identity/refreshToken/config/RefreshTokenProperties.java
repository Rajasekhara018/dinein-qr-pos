package com.heuristiq.uam.refreshToken.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Refresh-token rotation settings ({@code railcab.uam.refresh-token.*}).
 *
 * @param reuseGraceSeconds   how long after rotation the previous token is still accepted (a lost response or two
 *                            racing refreshes); it then returns the family's current successor instead of a new token.
 *                            0 turns the grace window off. Reuse after the window revokes the whole family.
 * @param maxGraceRedemptions how many times one rotated token may be redeemed inside the window; a further replay is
 *                            treated as reuse and revokes the family.
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.refresh-token")
public record RefreshTokenProperties(
        @DefaultValue("30") @Min(0) @Max(300) long reuseGraceSeconds,
        @DefaultValue("1") @Min(1) @Max(5) int maxGraceRedemptions) {
}
