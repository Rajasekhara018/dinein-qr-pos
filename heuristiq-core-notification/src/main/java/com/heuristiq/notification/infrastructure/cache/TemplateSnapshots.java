package com.railcab.nms.infrastructure.cache;

import com.railcab.nms.domain.model.NotificationTemplate;

/** Copies of templates, so a cached template is never shared with (and mutated by) a caller. */
final class TemplateSnapshots {

    private TemplateSnapshots() {
    }

    /** A field-by-field copy; all fields are immutable values (strings, enum, date-times, boolean). */
    static NotificationTemplate copyOf(NotificationTemplate source) {
        NotificationTemplate copy = new NotificationTemplate();
        copy.setId(source.getId());
        copy.setEvent(source.getEvent());
        copy.setChannel(source.getChannel());
        copy.setName(source.getName());
        copy.setSubject(source.getSubject());
        copy.setBody(source.getBody());
        copy.setExternalTemplateId(source.getExternalTemplateId());
        copy.setTenantId(source.getTenantId());
        copy.setActive(source.isActive());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
