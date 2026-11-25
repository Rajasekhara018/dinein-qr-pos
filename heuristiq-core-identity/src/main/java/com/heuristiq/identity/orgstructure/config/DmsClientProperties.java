package com.heuristiq.identity.orgstructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * How UAM reaches Rider_DMS to read uploaded import files.
 *
 * @param baseUrl            DMS service root, without the /api/v1/dms path
 * @param callTimeoutMs      per-attempt timeout; a download is slower than a lookup
 * @param maxAttempts        attempts for these read-only (safe to repeat) calls
 * @param maxConcurrentCalls simultaneous calls allowed before further ones are refused
 * @param maxFileBytes       largest file that will be read into memory
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms")
public record DmsClientProperties(
        @DefaultValue("http://localhost:9104") @NotBlank String baseUrl,
        @DefaultValue("20000") @Min(1000) long callTimeoutMs,
        @DefaultValue("3") @Min(1) int maxAttempts,
        @DefaultValue("5") @Min(1) int maxConcurrentCalls,
        @DefaultValue("26214400") @Min(1024) int maxFileBytes) {
}
