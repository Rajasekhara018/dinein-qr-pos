package com.railcab.audit.application;

import static com.railcab.audit.application.RetentionConstants.*;
import com.railcab.audit.config.AuditProperties;
import com.railcab.audit.domain.AuditEvent;
import com.railcab.audit.domain.AuditEventType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Decides how long an event is kept. Reads (GET, HEAD, OPTIONS) and screen views are the high-volume, low-value
 * classes (30 days by default); writes are kept 90 days; communication, payment-gateway and domain events 365.
 * The result is stored on the event as {@code expireAt}, so changing a setting affects new events only.
 */
@Component
public class RetentionPolicy {

    private final AuditProperties.Retention retention;

    /**
     * Creates the policy.
     *
     * @param properties service settings
     */
    public RetentionPolicy(AuditProperties properties) {
        this.retention = properties.getRetention();
    }

    /**
     * @param event the event (type and method are read)
     * @return days it is kept
     */
    public int daysFor(AuditEvent event) {
        AuditEventType type = event.getEventType();
        return switch (type) {
            case SCREEN_VIEW -> retention.getScreenViewDays();
            case COMMUNICATION -> retention.getCommunicationDays();
            case PAYMENT_GATEWAY -> retention.getPaymentGatewayDays();
            case DOMAIN -> retention.getDomainDays();
            case ACCESS -> event.getMethod() != null && READ_METHODS.contains(event.getMethod())
                    ? retention.getAccessReadDays() : retention.getAccessWriteDays();
        };
    }

    /**
     * @param event      the event
     * @param receivedAt when it reached the service
     * @return the instant after which it may be purged (counted from the receive time, so late events are not cut short)
     */
    public Instant expireAt(AuditEvent event, Instant receivedAt) {
        return receivedAt.plus(Duration.ofDays(daysFor(event)));
    }
}
