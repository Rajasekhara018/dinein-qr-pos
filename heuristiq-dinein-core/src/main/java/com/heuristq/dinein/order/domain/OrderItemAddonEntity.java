package com.heuristq.dinein.order.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "order_item_addon")
public class OrderItemAddonEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItemEntity orderItem;

    @Column(name = "addon_id")
    private Long addonId;

    @Column(name = "addon_name", nullable = false, length = 50)
    private String addonName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
