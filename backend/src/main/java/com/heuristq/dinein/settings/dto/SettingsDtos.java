package com.heuristq.dinein.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsResponse(
            String name, String address, String phone, String gstin, String fssaiNo,
            Long logoImageId, String logoUrl,
            boolean acceptingOrders, boolean pricesIncludeGst,
            LocalTime openingTime, LocalTime closingTime, String currency, String brandColor,
            int kitchenWarnMinutes, int kitchenAlertMinutes, int readyAutoHideMinutes, boolean takeawayEnabled) {
    }

    public record UpdateSettingsRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String address,
            @Pattern(regexp = "^$|^[0-9+\\- ]{6,15}$", message = "invalid phone number") String phone,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "invalid GSTIN") String gstin,
            @Pattern(regexp = "^$|^[0-9]{14}$", message = "FSSAI number must be 14 digits") String fssaiNo,
            Long logoImageId,
            boolean acceptingOrders,
            boolean pricesIncludeGst,
            LocalTime openingTime,
            LocalTime closingTime,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colour must be #RRGGBB") String brandColor,
            @Min(1) @Max(240) int kitchenWarnMinutes,
            @Min(1) @Max(240) int kitchenAlertMinutes,
            @Min(1) @Max(240) int readyAutoHideMinutes,
            /* Null keeps the current value (older clients do not send it). */
            Boolean takeawayEnabled) {
    }

    /** What guests see: branding and whether ordering is possible right now. */
    public record PublicRestaurantInfo(String name, String address, String phone, String gstin, String fssaiNo,
                                       String logoUrl, String brandColor, boolean acceptingOrders, boolean openNow,
                                       LocalTime openingTime, LocalTime closingTime, boolean pricesIncludeGst,
                                       boolean takeawayEnabled) {
    }
}
