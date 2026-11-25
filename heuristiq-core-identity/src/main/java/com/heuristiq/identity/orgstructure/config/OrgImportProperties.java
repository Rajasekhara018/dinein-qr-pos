package com.heuristiq.identity.orgstructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Limits of the bulk employee import.
 *
 * @param maxRows   most data rows one uploaded file may hold
 * @param chunkSize rows applied per transaction when a confirmed import runs
 * @param queueSize confirmed imports allowed to wait behind the one running
 */
@Validated
@ConfigurationProperties(prefix = "railcab.uam.org-import")
public record OrgImportProperties(
        @DefaultValue("200000") @Min(1) int maxRows,
        @DefaultValue("500") @Min(1) int chunkSize,
        @DefaultValue("5") @Min(0) int queueSize) {
}
