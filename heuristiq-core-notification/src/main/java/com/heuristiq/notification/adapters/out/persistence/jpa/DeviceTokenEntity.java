package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "nms_device_tokens", indexes = {
                @Index(name = "idx_nms_device_token_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_nms_device_token_user_id", columnList = "user_id"),
        @Index(name = "idx_nms_device_token_token", columnList = "token")
})
public class DeviceTokenEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "user_id")
    private String userId;

    @Column(length = 500)
    private String token;

    private String platform;

    @Column(name = "user_type", length = 64)
    private String userType;

    @Column(length = 64)
    private String app;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "os_version", length = 128)
    private String osVersion;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(length = 32)
    private String locale;

    @Column(length = 64)
    private String timezone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
