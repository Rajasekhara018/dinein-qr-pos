package com.railcab.nms.domain.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One provider send attempt for one recipient on one channel. A {@link NotificationLog} row is the
 * per-recipient summary; each retry made by the resilience layer is its own attempt row, so a
 * failure can be analysed from origin to destination: who asked, which template, which channel and
 * provider, what the provider answered, and how long it took.
 *
 * <p>Never holds the rendered body, an OTP or an unmasked recipient.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationAttempt {

    private String id;

    /** Id of the owning {@link NotificationLog} (per-recipient summary row). */
    private String logId;

    private String messageId;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    private String tenantId;

    private String correlationId;

    private String event;

    /** Origin: calling service. Optional. */
    private String sourceService;

    /** Origin: template or event code the caller named. Optional, defaults to the event key. */
    private String eventCode;

    /** Origin: the user whose action triggered the message. Optional. */
    private String triggeringUserId;

    /** Origin: type of the entity the message is about (for example BOOKING). Optional. */
    private String entityType;

    /** Origin: id of that entity. Optional. */
    private String entityId;

    private NotificationChannel channel;

    /** Concrete provider that was called (digimile, twilio, meta, fcm, smtp, ...). */
    private String providerName;

    /** The id the provider gave the message; the key a delivery receipt is matched on. */
    private String providerMessageId;

    /** 1-based attempt number within the log row. */
    private int attemptNumber;

    private DeliveryStatus status;

    /** Raw status text from the provider (send response or latest receipt). */
    private String providerStatus;

    private String errorCode;

    private String errorMessage;

    /** Always masked. */
    private String recipient;

    private Long latencyMs;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    /** When the last receipt changed {@link #status}. */
    private LocalDateTime statusUpdatedAt;

    /** Optimistic lock so two concurrent receipts cannot step a status backwards. */
    private Long version;
}
