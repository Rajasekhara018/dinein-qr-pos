package com.heuristq.dinein.kiosk.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/** "When X is added (or at checkout), suggest item Y." See V16 for how the triggers combine. */
@Getter
@Setter
@Entity
@Table(name = "kiosk_upsell_rule")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class KioskUpsellRuleEntity extends TenantOwnedEntity {

    public static final String ITEM_ADDED = "ITEM_ADDED";
    public static final String CHECKOUT = "CHECKOUT";

    @Column(name = "trigger_item_id")
    private Long triggerItemId;

    @Column(name = "trigger_category_id")
    private Long triggerCategoryId;

    @Column(name = "suggested_item_id", nullable = false)
    private Long suggestedItemId;

    @Column(nullable = false, length = 12)
    private String placement;

    @Column(length = 80)
    private String message;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;
}
