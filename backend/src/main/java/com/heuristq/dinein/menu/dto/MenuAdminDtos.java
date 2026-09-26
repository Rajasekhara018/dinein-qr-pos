package com.heuristq.dinein.menu.dto;

import com.heuristq.dinein.menu.domain.FoodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class MenuAdminDtos {

    private MenuAdminDtos() {
    }

    // ----- Categories --------------------------------------------------------------------------

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String description,
            Long imageId,
            Boolean active) {
    }

    public record CategoryResponse(Long id, String name, String description, Long imageId, String imageUrl,
                                   String thumbUrl, int displayOrder, boolean active, long itemCount) {
    }

    public record StatusRequest(@NotNull Boolean active) {
    }

    public record ReorderRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {
    }

    // ----- Items -------------------------------------------------------------------------------

    public record VariantRequest(
            Long id,
            @NotBlank @Size(max = 50) String name,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal price,
            boolean isDefault) {
    }

    public record AddonRequest(
            Long id,
            @NotBlank @Size(max = 50) String name,
            @NotNull @DecimalMin(value = "0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal price) {
    }

    public record ItemRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Long imageId,
            @DecimalMin(value = "0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
            @NotNull FoodType foodType,
            @NotNull @DecimalMin("0.00") @DecimalMax("28.00") @Digits(integer = 2, fraction = 2) BigDecimal gstPercent,
            Boolean available,
            @Size(max = 20) List<@Valid VariantRequest> variants,
            @Size(max = 30) List<@Valid AddonRequest> addons,
            /** Optimistic-lock version from the last read; null skips the check (create). */
            Long version) {
    }

    public record VariantResponse(Long id, String name, BigDecimal price, boolean isDefault) {
    }

    public record AddonResponse(Long id, String name, BigDecimal price) {
    }

    public record ItemResponse(Long id, Long categoryId, String categoryName, String name, String description,
                               Long imageId, String imageUrl, String thumbUrl, BigDecimal basePrice, FoodType foodType,
                               BigDecimal gstPercent, boolean available, boolean active, int displayOrder,
                               long version, BigDecimal displayPrice, boolean hasVariants,
                               List<VariantResponse> variants, List<AddonResponse> addons) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    public record VariantPrice(@NotNull Long id,
                               @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal price) {
    }

    /** Quick inline rate edit: either the base price or specific variant prices. */
    public record PriceRequest(
            @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
            @Size(max = 20) List<@Valid VariantPrice> variants,
            Long version) {
    }
}
