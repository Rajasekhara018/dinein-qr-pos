package com.heuristq.dinein.notification.domain;

import com.heuristq.dinein.notification.NotificationEvent;
import com.heuristq.dinein.notification.NotificationSeverity;
import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/** Read state is per staff user in {@code notification_read}, not on this row (see V2 migration). */
@Getter
@Setter
@Entity
@Table(name = "in_app_notification")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class InAppNotificationEntity extends TenantOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InAppAudience audience;

    @Column(nullable = false, length = 64)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationEvent event;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(length = 300)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationSeverity severity;

    @Column(name = "related_order_id")
    private Long relatedOrderId;
}
