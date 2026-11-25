package com.heuristiq.notification.adapters.in.web.callback;

import java.util.Locale;
import java.util.Optional;

import org.springframework.util.StringUtils;

import com.heuristiq.notification.domain.model.DeliveryStatus;

/**
 * Translates each provider's status vocabulary to {@link DeliveryStatus}. An empty result means the
 * provider reported something NMS does not track (for example a temporary failure that it will
 * retry itself); the callback is acknowledged and ignored.
 */
final class ReceiptStatusMapper {

    private ReceiptStatusMapper() {
    }

    static Optional<DeliveryStatus> fromDigimile(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (CallbackConstants.DIGIMILE_DELIVERED.contains(s)) {
            return Optional.of(DeliveryStatus.DELIVERED);
        }
        if (CallbackConstants.DIGIMILE_SENT.contains(s)) {
            return Optional.of(DeliveryStatus.SENT);
        }
        if (CallbackConstants.DIGIMILE_FAILED.contains(s)) {
            return Optional.of(DeliveryStatus.FAILED);
        }
        return Optional.empty();
    }

    static Optional<DeliveryStatus> fromTwilio(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "accepted", "scheduled", "queued", "sending" -> Optional.of(DeliveryStatus.QUEUED);
            case "sent" -> Optional.of(DeliveryStatus.SENT);
            case "delivered" -> Optional.of(DeliveryStatus.DELIVERED);
            case "read" -> Optional.of(DeliveryStatus.READ);
            case "undelivered" -> Optional.of(DeliveryStatus.BOUNCED);
            case "failed", "canceled" -> Optional.of(DeliveryStatus.FAILED);
            default -> Optional.empty();
        };
    }

    static Optional<DeliveryStatus> fromMeta(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "sent" -> Optional.of(DeliveryStatus.SENT);
            case "delivered" -> Optional.of(DeliveryStatus.DELIVERED);
            case "read" -> Optional.of(DeliveryStatus.READ);
            case "failed" -> Optional.of(DeliveryStatus.FAILED);
            default -> Optional.empty();
        };
    }

    /** Mailgun: only a permanent failure bounces; a temporary one is retried by Mailgun itself and ignored here. */
    static Optional<DeliveryStatus> fromMailgun(String event, String severity) {
        if (!StringUtils.hasText(event)) {
            return Optional.empty();
        }
        return switch (event.trim().toLowerCase(Locale.ROOT)) {
            case "accepted" -> Optional.of(DeliveryStatus.QUEUED);
            case "delivered" -> Optional.of(DeliveryStatus.DELIVERED);
            case "opened" -> Optional.of(DeliveryStatus.READ);
            case "rejected" -> Optional.of(DeliveryStatus.FAILED);
            case "failed" -> "permanent".equalsIgnoreCase(severity)
                    ? Optional.of(DeliveryStatus.BOUNCED) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** The generic endpoint takes NMS status names directly. */
    static Optional<DeliveryStatus> fromName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(DeliveryStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
