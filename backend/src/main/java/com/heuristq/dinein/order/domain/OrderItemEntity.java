package com.heuristq.dinein.order.domain;

import com.heuristq.dinein.menu.domain.FoodType;
import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** An order line. Name, variant, price and GST are snapshots: later menu edits never change past orders. */
@Getter
@Setter
@Entity
@Table(name = "order_item")
public class OrderItemEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @Column(name = "variant_name", length = 50)
    private String variantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", length = 10)
    private FoodType foodType;

    /** Variant/base price plus add-ons, per unit. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "gst_percent", nullable = false, precision = 4, scale = 2)
    private BigDecimal gstPercent;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(length = 200)
    private String notes;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OrderItemAddonEntity> addons = new ArrayList<>();

    public void addAddon(OrderItemAddonEntity addon) {
        addon.setOrderItem(this);
        addons.add(addon);
    }
}
