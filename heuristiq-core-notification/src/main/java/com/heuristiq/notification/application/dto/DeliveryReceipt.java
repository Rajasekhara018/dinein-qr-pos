package com.heuristiq.notification.application.dto;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.DeliveryStatus;

/**
 * A provider's report on a message it accepted earlier (SMS DLR, WhatsApp status, email event),
 * already translated to the NMS vocabulary by the inbound adapter.
 *
 * @param provider          concrete provider that sent the receipt (digimile, twilio, meta, mailgun...)
 * @param providerMessageId the id the provider gave the message when it accepted it
 * @param status            new status
 * @param providerStatus    raw status text, kept for diagnosis
 * @param errorCode         provider error code on failure, may be null
 * @param errorMessage      provider reason on failure, may be null
 * @param occurredAt        when the provider says it happened; null means now
 */
public record DeliveryReceipt(String provider, String providerMessageId, DeliveryStatus status,
        String providerStatus, String errorCode, String errorMessage, LocalDateTime occurredAt) {
}
