package com.heuristq.dinein.notification.domain;

import com.heuristq.dinein.notification.NotificationChannel;
import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_log")
public class NotificationLogEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 40)
    private String template;

    @Column(name = "recipient_masked", length = 120)
    private String recipientMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryStatus status;

    @Column(length = 500)
    private String error;

    @Column(name = "related_order_id")
    private Long relatedOrderId;
}
