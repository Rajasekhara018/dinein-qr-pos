package com.heuristiq.notification.search.weblist;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publishing of the NMS web lists ({@code WEB_NMS_*}) to Rider_SearchAPI ({@code railcab.search.web-lists.*}).
 *
 * @param enabled        whether the lists are sent at all
 * @param baseUrl        Rider_SearchAPI base URL ({@code SEARCH_API_BASE_URL})
 * @param serviceToken   shared service credential sent as {@code X-Rider-Service-Token} ({@code RIDER_INTERNAL_SERVICE_TOKEN})
 * @param refreshMs      pause between two full publications
 * @param initialDelayMs pause before the first full publication after start
 * @param changeDelayMs  pause between a change and sending the lists it affects (changes in that window are sent once)
 */
@ConfigurationProperties(prefix = "railcab.search.web-lists")
public record WebListProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:9116") String baseUrl,
        @DefaultValue("") String serviceToken,
        @DefaultValue("600000") long refreshMs,
        @DefaultValue("60000") long initialDelayMs,
        @DefaultValue("2000") long changeDelayMs) {
}
