package com.heuristq.dinein.menu.domain;

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
@Table(name = "category")
public class CategoryEntity extends BaseEntity {

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId = RestaurantEntity.DEFAULT_ID;

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
}
