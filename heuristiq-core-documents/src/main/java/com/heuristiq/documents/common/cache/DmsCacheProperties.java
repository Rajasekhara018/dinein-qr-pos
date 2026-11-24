package com.heuristiq.documents.common.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Read-cache settings ({@code railcab.dms.cache.*}).
 *
 * @param enabled          global switch; false turns every DMS cache into a pass-through
 * @param documentTypeRule the document-type rule cache
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.cache")
public record DmsCacheProperties(
        @DefaultValue(DmsCacheConstants.DEFAULT_ENABLED) boolean enabled,
        @Valid @NotNull @DefaultValue Spec documentTypeRule
) {

    /**
     * One cache.
     *
     * @param enabled    per-cache switch (also needs the global one)
     * @param ttlSeconds seconds after which an entry is reloaded from the database
     * @param maxSize    maximum number of entries; the least useful are evicted beyond it
     */
    public record Spec(
            @DefaultValue(DmsCacheConstants.DEFAULT_ENABLED) boolean enabled,
            @Min(1) @Max(DmsCacheConstants.MAX_TTL_SECONDS) @DefaultValue(DmsCacheConstants.DEFAULT_RULE_TTL_SECONDS) long ttlSeconds,
            @Min(1) @Max(DmsCacheConstants.MAX_SIZE) @DefaultValue(DmsCacheConstants.DEFAULT_RULE_MAX_SIZE) long maxSize
    ) {
    }
}
