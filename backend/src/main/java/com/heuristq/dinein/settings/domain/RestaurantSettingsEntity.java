package com.heuristq.dinein.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;

/** Single-row table (id = 1). */
@Getter
@Setter
@Entity
@Table(name = "restaurant_settings")
public class RestaurantSettingsEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 15)
    private String phone;

    @Column(length = 15)
    private String gstin;

    @Column(name = "fssai_no", length = 20)
    private String fssaiNo;

    @Column(name = "logo_image_id")
    private Long logoImageId;

    @Column(name = "is_accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(name = "prices_include_gst", nullable = false)
    private boolean pricesIncludeGst;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency = "INR";

    @Column(name = "brand_color", nullable = false, length = 7)
    private String brandColor = "#C2410C";

    @Column(name = "kitchen_warn_minutes", nullable = false)
    private int kitchenWarnMinutes = 10;

    @Column(name = "kitchen_alert_minutes", nullable = false)
    private int kitchenAlertMinutes = 20;

    @Column(name = "ready_auto_hide_minutes", nullable = false)
    private int readyAutoHideMinutes = 15;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
