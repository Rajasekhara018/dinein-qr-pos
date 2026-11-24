package com.heuristq.dinein.menu.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "item")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class ItemEntity extends TenantOwnedEntity {

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", nullable = false, length = 10)
    private FoodType foodType;

    @Column(name = "gst_percent", nullable = false, precision = 4, scale = 2)
    private BigDecimal gstPercent = new BigDecimal("5.00");

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    private long version;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ItemVariantEntity> variants = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<AddonEntity> addons = new ArrayList<>();

    public List<ItemVariantEntity> activeVariants() {
        return variants.stream().filter(ItemVariantEntity::isActive).toList();
    }

    public List<AddonEntity> activeAddons() {
        return addons.stream().filter(AddonEntity::isActive).toList();
    }

    public boolean hasActiveVariants() {
        return variants.stream().anyMatch(ItemVariantEntity::isActive);
    }
}
