package com.heuristq.dinein.menu.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Getter
@Setter
@Entity
@Table(name = "category")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class CategoryEntity extends TenantOwnedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    /** Reference only; image bytes are never mapped here. */
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Which of this restaurant's own {@link KitchenStationEntity} rows this category's items are routed to; null
     * means unassigned (shown on every KDS screen). Snapshotted onto each order item at placement.
     */
    @Column(name = "station_id")
    private Long stationId;
}
