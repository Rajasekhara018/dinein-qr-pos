package com.railcab.nms.infrastructure.cache;

import com.railcab.nms.domain.model.NotificationChannel;

/**
 * Key of the resolved-template cache. The tenant part is always explicit: {@code tenant:<id>} for an organization and
 * {@code shared} for the shared template, so one organization's entry can never be returned for another.
 */
record TemplateCacheKey(String tenantKey, String event, NotificationChannel channel) {

    static TemplateCacheKey of(String tenantId, String event, NotificationChannel channel) {
        String tenantKey = tenantId == null || tenantId.isBlank()
                ? NmsCacheConstants.SHARED_TENANT_KEY
                : NmsCacheConstants.TENANT_KEY_PREFIX + tenantId.trim();
        return new TemplateCacheKey(tenantKey, event, channel);
    }
}
