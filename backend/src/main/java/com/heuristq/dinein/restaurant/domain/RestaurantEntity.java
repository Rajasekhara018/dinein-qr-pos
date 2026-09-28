package com.heuristq.dinein.restaurant.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A tenant. Every restaurant/cafe on the platform is one row here; restaurant-owned data carries its id. */
@Getter
@Setter
@Entity
@Table(name = "restaurant")
public class RestaurantEntity extends BaseEntity {

    /** The single restaurant seeded before multi-tenancy existed; tenant-owned rows default to it until scoping lands. */
    public static final long DEFAULT_ID = 1L;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 60)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;
}
