package com.railcab.nms.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DeviceToken {

    private String id;

    private String userId;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    private String tenantId;

    private String token;

    private String platform;

    /** CUSTOMER / DRIVER / ... -- from the registering user's login token. */
    private String userType;

    /** Which app install registered this token (e.g. CUSTOMER_APP, DRIVER_APP). */
    private String app;

    private String deviceModel;

    private String osVersion;

    private String appVersion;

    private String locale;

    private String timezone;

    private LocalDateTime createdAt;

    /** Last time this device (re-)registered -- a stale value suggests an uninstalled/inactive device. */
    private LocalDateTime lastSeenAt;
}
