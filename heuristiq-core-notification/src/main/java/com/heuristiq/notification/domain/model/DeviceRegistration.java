package com.railcab.nms.domain.model;

/**
 * What a client reports when it registers an FCM token, beyond the token itself. Everything except
 * {@code platform} is optional (older app builds send only token + platform). {@code userType} is
 * derived server-side from the caller's login token, never trusted from the request body; {@code tenantId} is
 * the caller's organization, likewise taken from the token.
 */
public record DeviceRegistration(
        String platform,
        String userType,
        String app,
        String deviceModel,
        String osVersion,
        String appVersion,
        String locale,
        String timezone,
        String tenantId) {

    /** Registration without an organization (the use case stamps the caller's before storing). */
    public DeviceRegistration(String platform, String userType, String app, String deviceModel, String osVersion,
            String appVersion, String locale, String timezone) {
        this(platform, userType, app, deviceModel, osVersion, appVersion, locale, timezone, null);
    }

    public static DeviceRegistration platformOnly(String platform) {
        return new DeviceRegistration(platform, null, null, null, null, null, null, null, null);
    }

    /** The same registration belonging to the given organization. */
    public DeviceRegistration withTenantId(String organizationId) {
        return new DeviceRegistration(platform, userType, app, deviceModel, osVersion, appVersion, locale, timezone,
                organizationId);
    }
}
