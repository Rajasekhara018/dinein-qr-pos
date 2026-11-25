package com.heuristiq.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * One screen-view report from an Angular or Flutter client. The user id and role are NOT part of it: they are
 * read from the caller's token.
 */
@Schema(description = "A screen the signed-in user opened.")
public record ScreenViewInput(
        @Schema(description = "Optional client-generated unique id (8-64 chars) so a retried batch is stored once.") String eventId,
        @Schema(description = "Stable screen key, lower-case dotted, never translated or renamed once released.",
                example = "payments.refunds") String screenKey,
        @Schema(description = "When the screen opened (ISO-8601); defaults to the receive time.") Instant occurredAt,
        @Schema(description = "WEB, ADMIN_WEB, WALLET_WEB, CUSTOMER_APP, DRIVER_APP or RESTAURANT_APP.", example = "ADMIN_WEB") String appChannel,
        @Schema(description = "Feature the screen belongs to, same lower-case dotted format.", example = "payments") String feature,
        @Schema(description = "Milliseconds the screen was open, when known.") Long durationMs,
        @Schema(description = "Optional: appVersion, platform, previousScreenKey. Anything else is ignored.") Map<String, Object> details) {
}
