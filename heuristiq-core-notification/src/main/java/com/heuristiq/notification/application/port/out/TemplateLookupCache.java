package com.railcab.nms.application.port.out;

import java.util.Optional;
import java.util.function.Supplier;

import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationTemplate;

/**
 * Remembers which template a send resolves to, per organization (or shared), event and channel, so the send path does not
 * query the database for every message. Implementations hand out private copies, never a shared mutable template.
 */
public interface TemplateLookupCache {

    /**
     * The resolved template for the key, running {@code loader} only when it is not already known. A loader that throws is
     * not remembered. An empty result is remembered briefly.
     *
     * @param tenantId the organization, or null/blank for the shared template
     * @param event    the already normalized event
     */
    Optional<NotificationTemplate> resolve(String tenantId, String event, NotificationChannel channel,
                                           Supplier<Optional<NotificationTemplate>> loader);

    /** Forgets everything; called after any template is created, changed, deleted or seeded. */
    void evictAll();
}
