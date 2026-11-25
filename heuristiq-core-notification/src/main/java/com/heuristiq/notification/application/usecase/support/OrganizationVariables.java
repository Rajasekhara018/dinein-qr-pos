package com.heuristiq.notification.application.usecase.support;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.heuristiq.notification.application.dto.OrganizationProfile;
import com.heuristiq.notification.application.port.out.OrganizationProfileResolver;

/**
 * The variables every message gets about the organization that sends it, so one template reads right for every
 * organization: {@code {{organizationName}}}, {@code {{organizationCode}}}, {@code {{organizationSupportEmail}}} and
 * {@code {{organizationSupportPhone}}}. A value the caller sends under the same name wins. With no organization, or when
 * it cannot be looked up, the name falls back to the platform's and the other values are empty, so a template never
 * shows a raw placeholder.
 */
@Component
public class OrganizationVariables {

    public static final String NAME = "organizationName";
    public static final String CODE = "organizationCode";
    public static final String SUPPORT_EMAIL = "organizationSupportEmail";
    public static final String SUPPORT_PHONE = "organizationSupportPhone";

    private final OrganizationProfileResolver resolver;
    private final String fallbackName;

    public OrganizationVariables(OrganizationProfileResolver resolver,
                                 @Value("${nms.organization.fallback-name:RealBoom}") String fallbackName) {
        this.resolver = resolver;
        this.fallbackName = fallbackName;
    }

    /** {@code supplied} plus the organization's variables; the caller's own values are kept. */
    public Map<String, String> merge(String tenantId, Map<String, String> supplied) {
        Map<String, String> merged = new HashMap<>();
        OrganizationProfile profile = tenantId == null || tenantId.isBlank() ? null : resolver.resolve(tenantId).orElse(null);
        merged.put(NAME, profile != null && notBlank(profile.name()) ? profile.name() : fallbackName);
        merged.put(CODE, profile != null && profile.code() != null ? profile.code() : "");
        merged.put(SUPPORT_EMAIL, profile != null && profile.supportEmail() != null ? profile.supportEmail() : "");
        merged.put(SUPPORT_PHONE, profile != null && profile.supportPhone() != null ? profile.supportPhone() : "");
        if (supplied != null) {
            supplied.forEach((key, value) -> {
                if (key != null && value != null) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
