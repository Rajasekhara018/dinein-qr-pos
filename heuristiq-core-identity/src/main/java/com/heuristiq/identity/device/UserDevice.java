package com.heuristiq.uam.device;

import java.time.LocalDateTime;
import java.util.Map;

import com.heuristiq.uam.enums.UamEnums.DeviceType;
import com.heuristiq.uam.enums.UamEnums.Platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code device.persistence.mongo.UserDeviceDocument} / {@code device.persistence.jpa.UserDeviceEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    private String id;

    private String userId;

    private String deviceFingerprint;

    private String deviceUuid;

    private String deviceName;

    private DeviceType deviceType;

    private String operatingSystem;

    private String operatingSystemVersion;

    private String browser;

    private String browserVersion;

    private String manufacturer;

    private String model;

    private String pushNotificationToken;

    private Platform platform;

    private Boolean trusted = false;

    private Boolean verified = false;

    private Boolean blocked = false;

    private String blockReason;

    private LocalDateTime lastLoginAt;

    private LocalDateTime lastLogoutAt;

    private String lastIpAddress;

    private String country;

    private String state;

    private String city;

    private LocalDateTime registeredAt;

    private LocalDateTime lastActiveAt;

    private Boolean active = true;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    private Long version;
}
