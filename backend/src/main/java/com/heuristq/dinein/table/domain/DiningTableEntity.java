package com.heuristq.dinein.table.domain;

import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "dining_table")
public class DiningTableEntity extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId = RestaurantEntity.DEFAULT_ID;

    /** Unique per restaurant (see uq_dining_table_restaurant_label), not globally. */
    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
