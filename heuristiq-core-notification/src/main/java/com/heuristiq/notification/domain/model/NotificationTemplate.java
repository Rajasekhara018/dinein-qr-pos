package com.heuristiq.notification.domain.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.search.SearchIndexable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * One record = one channel-specific template for one event.
 * Body is Base64-encoded HTML/text with {{variable}} placeholders.
 */
@Data
public class NotificationTemplate implements SearchIndexable {

    @Schema(description = "Template id (absent on create)", example = "6620e1f2a1b2c3d4e5f6a7b8")
    private String id;

    @NotBlank(message = "event must not be blank")
    @Schema(description = "Event key this template renders for", example = "RIDE_BOOKED")
    private String event;

    @NotNull(message = "channel must not be null")
    @Schema(description = "Channel this template is rendered for")
    private NotificationChannel channel;

    /** Human-readable name for UI display */
    @NotBlank(message = "name must not be blank")
    @Schema(description = "Human-readable name for UI display", example = "Ride Booked - Email")
    private String name;

    /** Email subject line (EMAIL channel only) */
    @Schema(description = "Email subject line (EMAIL channel only)", example = "Your ride is booked!")
    private String subject;

    /**
     * Base64-encoded template body.
     * Supports {{variable}} placeholder substitution.
     * EMAIL: HTML. SMS/WHATSAPP: plain text. FCM_PUSH: JSON data payload.
     */
    @NotBlank(message = "body must not be blank")
    @Schema(description = "Base64-encoded template body. Supports {{variable}} placeholder substitution. "
            + "EMAIL: HTML. SMS/WHATSAPP: plain text. FCM_PUSH: JSON data payload.")
    private String body;

    /** DLT/WhatsApp pre-approved template ID (SMS / WHATSAPP channels) */
    @Schema(description = "DLT/WhatsApp pre-approved template ID (SMS / WHATSAPP channels)")
    private String externalTemplateId;

    /**
     * The organization this template belongs to; null for a shared template every organization uses. An organization's
     * own template for an event and channel is used instead of the shared one when that organization sends.
     */
    @Schema(description = "Organization that owns this template; absent for a shared template. An organization's own template "
            + "replaces the shared one for that organization")
    private String tenantId;

    @Schema(description = "Whether the template is active", example = "true")
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable. Body is deliberately
    // never indexed -- it's Base64-encoded and not human-searchable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return NotificationDomainConstants.TEMPLATE_SEARCH_MODEL;
    }

    @JsonIgnore
    @Override
    public String getSearchModelKey() {
        return id;
    }

    @JsonIgnore
    @Override
    public Map<String, Object> getSearchIndexFields() {
        Map<String, Object> fields = new HashMap<>();
        // The owning organization, so search shows an organization its own templates plus the shared ones (no owner).
        fields.put("ownerKey", tenantId);
        fields.put("text1", name);
        fields.put("text2", event);
        fields.put("text3", channel != null ? channel.name() : null);
        fields.put("text4", active ? "ACTIVE" : "INACTIVE");
        fields.put("text5", externalTemplateId);
        if (createdAt != null) {
            fields.put("date1", createdAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (updatedAt != null) {
            fields.put("date2", updatedAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
