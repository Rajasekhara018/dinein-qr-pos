package com.heuristq.dinein.menu.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A physical KDS screen for this restaurant (e.g. "Grill", "Espresso Bar", "Tawa"), freely named by the owner --
 * there is no fixed list, since what a pizzeria needs differs from a cafe or a dosa counter. A {@link CategoryEntity}
 * may route to one; see {@code OrderItemEntity#stationId}/{@code stationName} for how a placed order snapshots it.
 */
@Getter
@Setter
@Entity
@Table(name = "kitchen_station")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class KitchenStationEntity extends TenantOwnedEntity {

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
