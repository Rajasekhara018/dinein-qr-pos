package com.railcab.nms.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Document(collection = "nms_in_app_notifications")
@Data
public class InAppNotificationDocument {

    @Id
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Indexed
    private String tenantId;

    @Indexed
    private String userId;

    private String title;

    private String message;

    private boolean read;

    @Indexed
    private LocalDateTime createdAt;
}
