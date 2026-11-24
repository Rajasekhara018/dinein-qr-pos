package com.heuristq.dinein.menu.dto;

import com.heuristq.dinein.menu.domain.FoodType;

import java.math.BigDecimal;
import java.util.List;

/** Guest-facing menu. Contains image URLs only, never image bytes. */
public final class PublicMenuDtos {

    private PublicMenuDtos() {
    }

    public record MenuResponse(String version, boolean pricesIncludeGst, List<MenuCategory> categories) {
    }

    public record MenuCategory(Long id, String name, String description, String imageUrl, String thumbUrl,
                               List<MenuItem> items) {
    }

    public record MenuItem(Long id, String name, String description, FoodType foodType, BigDecimal basePrice,
                           BigDecimal displayPrice, BigDecimal gstPercent, boolean available, String imageUrl,
                           String thumbUrl, List<MenuVariant> variants, List<MenuAddon> addons) {
    }

    public record MenuVariant(Long id, String name, BigDecimal price, boolean isDefault) {
    }

    public record MenuAddon(Long id, String name, BigDecimal price) {
    }
}
