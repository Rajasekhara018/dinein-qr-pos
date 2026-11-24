package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "nms_device_tokens")
@Data
public class DeviceTokenDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    @Indexed
    private String userId;

    @Indexed
    private String token;

    private String platform;

    private String userType;

    private String app;

    private String deviceModel;

    private String osVersion;

    private String appVersion;

    private String locale;

    private String timezone;

    private LocalDateTime createdAt;

    private LocalDateTime lastSeenAt;
}
