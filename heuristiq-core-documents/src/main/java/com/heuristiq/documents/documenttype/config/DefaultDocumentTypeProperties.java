package com.railcab.dms.documenttype.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The built-in {@code DEFAULT} document type every organization gets ({@code railcab.dms.default-document-type.*}).
 *
 * @param enabled       false stops DMS creating the type and using it as the fallback for an unknown type code
 * @param maxFileSizeMb largest file the default type accepts, 1 to 50 (the same bounds as any document type)
 * @param retentionDays days uploads of the default type are kept; 0 or less means the service default retention
 * @param prebuiltCatalogEnabled true (default) to also create the prebuilt catalog of customer, driver and franchise
 *                      onboarding types for every organization; their sizes are capped by {@code maxFileSizeMb}
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.default-document-type")
public record DefaultDocumentTypeProperties(
        @DefaultValue("true") boolean enabled,
        @Min(1) @Max(50) @DefaultValue("10") int maxFileSizeMb,
        @DefaultValue("0") int retentionDays,
        @DefaultValue("true") boolean prebuiltCatalogEnabled
) {
}
