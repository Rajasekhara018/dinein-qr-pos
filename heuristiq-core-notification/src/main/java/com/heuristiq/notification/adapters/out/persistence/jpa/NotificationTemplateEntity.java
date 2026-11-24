package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import com.heuristiq.notification.domain.model.NotificationChannel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

/**
 * One row = one channel-specific template for one event.
 * Body is Base64-encoded HTML/text with {{variable}} placeholders.
 */
@Data
@Entity
@Table(name = "nms_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_nms_template_event_channel_tenant", columnNames = {"event", "channel", "tenant_id"})
})
public class NotificationTemplateEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String event;

    /** Owning organization; null for a shared template. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    /** Human-readable name for UI display */
    private String name;

    /** Email subject line (EMAIL channel only) */
    private String subject;

    /**
     * Base64-encoded template body.
     * Supports {{variable}} placeholder substitution.
     * EMAIL: HTML. SMS/WHATSAPP: plain text. FCM_PUSH: JSON data payload.
     */
    @Column(length = 4000)
    private String body;

    /** DLT/WhatsApp pre-approved template ID (SMS / WHATSAPP channels) */
    private String externalTemplateId;

    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
