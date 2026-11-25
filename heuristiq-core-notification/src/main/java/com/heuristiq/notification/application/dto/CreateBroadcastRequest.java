package com.heuristiq.notification.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.heuristiq.notification.domain.model.BroadcastSegment;
import com.heuristiq.notification.domain.model.NotificationChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateBroadcastRequest(
        @Schema(description = "Human-readable broadcast title", example = "Monsoon service advisory")
        String title,

        @NotBlank(message = "event must not be blank")
        @Schema(description = "Event key this broadcast's templates are rendered from", example = "SERVICE_ADVISORY")
        String event,

        @Schema(description = "Template variable substitutions, matching {{key}} placeholders in the template body")
        Map<String, String> variables,

        @NotNull(message = "segment must not be null")
        @Schema(description = "Audience segment this broadcast targets", example = "USERS")
        BroadcastSegment segment,

        @NotEmpty(message = "channels must not be empty")
        @Schema(description = "Channels to dispatch the broadcast on")
        List<NotificationChannel> channels,

        /** Null/past = dispatched on the next sweep; future = held until then. */
        @Schema(description = "When to dispatch. Null or a past timestamp dispatches on the next sweep; a future timestamp holds it until then.",
                example = "2026-07-20T09:00:00")
        LocalDateTime scheduledAt
) {
}
