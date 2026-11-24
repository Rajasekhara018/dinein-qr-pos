package com.heuristiq.uam.device.persistence.jpa;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.enums.UamEnums.DeviceType;
import com.heuristiq.uam.enums.UamEnums.Platform;
import com.heuristiq.uam.util.JsonMapAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_devices", uniqueConstraints = @UniqueConstraint(name = "uk_user_device", columnNames = {"user_id", "device_fingerprint"}))
public class UserDeviceEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank(message = "User Id is required")
    private String userId;

    @NotBlank
    private String deviceFingerprint;

    private String deviceUuid;

    @Size(max = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    private String operatingSystem;

    private String operatingSystemVersion;

    private String browser;

    private String browserVersion;

    private String manufacturer;

    private String model;

    private String pushNotificationToken;

    @Enumerated(EnumType.STRING)
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

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
