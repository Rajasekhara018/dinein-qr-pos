package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.railcab.nms.domain.model.NotificationChannel;
import lombok.Data;

/**
 * One document = one channel-specific template for one event.
 * Body is Base64-encoded HTML/text with {{variable}} placeholders.
 */
@Document(collection = "nms_templates")
@CompoundIndex(name = "event_channel_tenant_idx", def = "{'event': 1, 'channel': 1, 'tenantId': 1}", unique = true)
@Data
public class NotificationTemplateDocument {

    @Id
    private String id;

    private String event;

    /** Owning organization; null for a shared template. */
    private String tenantId;

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
    private String body;

    /** DLT/WhatsApp pre-approved template ID (SMS / WHATSAPP channels) */
    private String externalTemplateId;

    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
