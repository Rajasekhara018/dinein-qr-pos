package com.railcab.dms.documenttype.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How DMS reaches Rider_WorkflowService for maker-checker and how Workflow reaches DMS back
 * ({@code railcab.dms.workflow.*}).
 *
 * @param baseUrl         explicit Workflow address; when blank the Eureka name in {@code discoveryUrl} is used
 * @param discoveryUrl    Eureka-resolved address of Rider_WorkflowService, e.g. {@code http://RealBoom-WorkflowService}
 * @param callbackBaseUrl externally reachable base URL of THIS service (host:port, no path) that Workflow calls to apply
 *                        an approved change; the apply path is appended. Required to submit changes.
 * @param timeoutMs       call timeout for the submit
 */
@Validated
@ConfigurationProperties(prefix = "railcab.dms.workflow")
public record DocumentTypeWorkflowProperties(
        @DefaultValue("") String baseUrl,
        @NotBlank @DefaultValue("http://RealBoom-WorkflowService") String discoveryUrl,
        @DefaultValue("") String callbackBaseUrl,
        @Min(200) @DefaultValue("3000") long timeoutMs
) {
}
