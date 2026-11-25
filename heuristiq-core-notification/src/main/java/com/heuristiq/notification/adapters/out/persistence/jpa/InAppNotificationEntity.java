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
@Table(name = "nms_in_app_notifications", indexes = {
                @Index(name = "idx_nms_in_app_tenant_user", columnList = "tenant_id, user_id"),
        @Index(name = "idx_nms_in_app_user_id", columnList = "user_id"),
        @Index(name = "idx_nms_in_app_created_at", columnList = "created_at")
})
public class InAppNotificationEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** Organization this record belongs to; null for a record stored before organizations existed. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "user_id")
    private String userId;

    private String title;

    @Column(length = 4000)
    private String message;

    private boolean read;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
