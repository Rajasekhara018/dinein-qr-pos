package com.heuristq.dinein.restaurant.dto;

import com.heuristq.dinein.restaurant.domain.RestaurantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record OwnerSummary(Long id, String username, String displayName, boolean active) {
    }

    public record RestaurantSummary(Long id, String name, String slug, RestaurantStatus status, Instant createdAt,
                                    List<OwnerSummary> owners) {
    }

    /**
     * {@code slug} is optional; when blank it's derived from {@code restaurantName}. Every field below this is
     * optional and mirrors what the owner would otherwise set up themselves at {@code /admin/settings} (see
     * {@code SettingsDtos.UpdateSettingsRequest}) or {@code /admin/staff} (see {@code StaffRequests.CreateStaff})
     * after their first login -- collecting it up front just saves them the trip. Anything left blank keeps
     * {@code RestaurantSettingsEntity}'s own defaults (e.g. {@code pricesIncludeGst=false},
     * {@code takeawayEnabled=true}, {@code brandColor="#C2410C"}).
     */
    public record OnboardRestaurantRequest(
            @NotBlank @Size(max = 100) String restaurantName,
            @Size(max = 60) String slug,
            @Size(max = 300) String address,
            @Pattern(regexp = "^$|^[0-9+\\- ]{6,15}$", message = "invalid phone number") String phone,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "invalid GSTIN") String gstin,
            @Pattern(regexp = "^$|^[0-9]{14}$", message = "FSSAI number must be 14 digits") String fssaiNo,
            Boolean pricesIncludeGst,
            LocalTime openingTime,
            LocalTime closingTime,
            @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "colour must be #RRGGBB") String brandColor,
            Boolean takeawayEnabled,
            @Size(max = 80) String ownerDisplayName,
            /** Blank derives {@code {slug}.owner}, matching the previous behaviour. */
            @Size(max = 50) @Pattern(regexp = "^$|^[A-Za-z0-9._-]+$",
                    message = "letters, digits, dot, dash, underscore only") String ownerUsername,
            @Email @Size(max = 120) String ownerEmail,
            @Pattern(regexp = "^(\\d{10})?$", message = "Phone must be 10 digits") String ownerPhone,
            /** Blank auto-generates one, matching the previous behaviour. */
            @Size(max = 72) String ownerPassword) {
    }

    /**
     * {@code temporaryPassword} is returned once, here, and never stored or logged in plain text; hand it to the
     * merchant out of band. {@code ownerUsername} must be used to sign in ({@code POST /api/v1/auth/login}), which
     * forces a password change on first use.
     */
    public record OnboardRestaurantResponse(Long restaurantId, String restaurantName, String slug,
                                            String ownerUsername, String temporaryPassword) {
    }
}
