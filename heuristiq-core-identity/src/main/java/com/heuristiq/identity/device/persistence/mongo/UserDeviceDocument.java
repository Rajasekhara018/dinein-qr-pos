package com.heuristiq.identity.device.persistence.mongo;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.enums.UamEnums.DeviceType;
import com.heuristiq.identity.enums.UamEnums.Platform;

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
@Document(collection = "user_devices")
@CompoundIndexes({
    @CompoundIndex(name = "uk_user_device", def = "{'userId':1,'deviceFingerprint':1}", unique = true),
    @CompoundIndex(name = "idx_user_active", def = "{'userId':1,'active':1}")
})
public class UserDeviceDocument {

    @Id
    private String id;

    @NotBlank(message = "User Id is required")
    @Indexed
    private String userId;

    @NotBlank
    private String deviceFingerprint;

    @Indexed
    private String deviceUuid;

    @Size(max = 100)
    private String deviceName;

    @Indexed
    private DeviceType deviceType;

    @Indexed
    private String operatingSystem;

    private String operatingSystemVersion;

    @Indexed
    private String browser;

    private String browserVersion;

    private String manufacturer;

    private String model;

    private String pushNotificationToken;

    @Indexed
    private Platform platform;

    @Indexed
    private Boolean trusted = false;

    private Boolean verified = false;

    @Indexed
    private Boolean blocked = false;

    private String blockReason;

    @Indexed
    private LocalDateTime lastLoginAt;

    private LocalDateTime lastLogoutAt;

    private String lastIpAddress;

    private String country;

    private String state;

    private String city;

    @Indexed
    private LocalDateTime registeredAt;

    @Indexed
    private LocalDateTime lastActiveAt;

    @Indexed
    private Boolean active = true;

    private Map<String, Object> metadata;

    @Indexed
    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
