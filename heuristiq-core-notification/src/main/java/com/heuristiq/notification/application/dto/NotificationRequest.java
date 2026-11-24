package com.railcab.nms.application.dto;

import java.util.List;
import java.util.Map;

import com.railcab.nms.domain.model.NotificationChannel;

import com.heuristiq.phone.validation.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload to trigger a notification dispatch — sent via REST or Kafka.
 *
 * <p>The service looks up the active template for each enabled channel of the event,
 * renders it with {@code variables}, and dispatches to the matching recipients.
 *
 * <p>Channel-specific recipients:
 * <ul>
 *   <li>{@code toEmail} — EMAIL channel</li>
 *   <li>{@code toPhone} — SMS and/or WHATSAPP channel</li>
 *   <li>{@code fcmTokens} — FCM_PUSH channel (falls back to a {@code userIds} device-token lookup if empty)</li>
 *   <li>{@code userIds} — IN_APP channel, and the FCM_PUSH fallback above</li>
 * </ul>
 *
 * <p>If {@code channels} is non-empty only those channels are attempted; otherwise
 * every channel that has an active template for the event is attempted.
 */
@Data
public class NotificationRequest {

    @Schema(description = "Idempotency key for this dispatch; auto-derived from the envelope messageID if omitted", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
    private String messageId;

    @Schema(description = "Correlation id shared by all notifications from one logical dispatch", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
    private String correlationId;

    @Size(max = 64, message = "sourceService must be at most 64 characters")
    @Schema(description = "Origin: the service asking for this notification (optional, for tracing origin to destination)", example = "rider-payment")
    private String sourceService;

    @Size(max = 128, message = "eventCode must be at most 128 characters")
    @Schema(description = "Origin: event or template code of the caller (optional; defaults to event)", example = "PAYMENT_CAPTURED")
    private String eventCode;

    @Size(max = 128, message = "triggeringUserId must be at most 128 characters")
    @Schema(description = "Origin: id of the user whose action triggered this notification (optional)")
    private String triggeringUserId;

    @Size(max = 64, message = "entityType must be at most 64 characters")
    @Schema(description = "Origin: type of the entity the notification is about (optional)", example = "BOOKING")
    private String entityType;

    @Size(max = 128, message = "entityId must be at most 128 characters")
    @Schema(description = "Origin: id of that entity (optional)")
    private String entityId;

    @Size(max = 128, message = "tenantId must be at most 128 characters")
    @Schema(description = "Organization the notification belongs to. Honoured only for backend callers (internal API key, "
            + "service token, Kafka); for a person's session the organization of the token always wins and this is ignored")
    private String tenantId;

    @NotBlank(message = "event must not be blank")
    @Schema(description = "Event key whose active templates drive this dispatch", example = "RIDE_BOOKED")
    private String event;

    /** Template variable substitutions — key matches {{key}} placeholders in the template body */
    @Schema(description = "Template variable substitutions, matching {{key}} placeholders in the template body")
    private Map<String, String> variables;

    /** Email recipients (EMAIL channel) */
    @Schema(description = "Email recipients (EMAIL channel)")
    private List<@Email(message = "must be a valid email address") String> toEmail;

    /** Phone number — used for both SMS and WHATSAPP channels */
    @ValidPhone(message = "must be a valid phone number (E.164, e.g. +919876543210, or a 10-digit Indian number)")
    @Schema(description = "Phone number used for both SMS and WHATSAPP channels", example = "+919876543210")
    private String toPhone;

    /** FCM device tokens (FCM_PUSH channel) */
    @Schema(description = "FCM device tokens (FCM_PUSH channel)")
    private List<@NotBlank(message = "fcmTokens entries must not be blank") String> fcmTokens;

    /** Target user IDs (IN_APP channel; also a fallback for FCM_PUSH token lookup) */
    @Schema(description = "Target user IDs (IN_APP channel; also a fallback for FCM_PUSH token lookup)")
    private List<String> userIds;

    /**
     * Optional channel override.
     * When empty/null all channels with an active template are attempted.
     */
    @Schema(description = "Optional channel override. When empty/null, all channels with an active template are attempted.")
    private List<NotificationChannel> channels;
}
