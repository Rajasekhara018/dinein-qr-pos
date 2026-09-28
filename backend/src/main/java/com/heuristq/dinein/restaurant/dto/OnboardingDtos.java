package com.heuristq.dinein.restaurant.dto;

import com.heuristq.dinein.restaurant.domain.RestaurantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    public record OwnerSummary(Long id, String username, String displayName, boolean active) {
    }

    public record RestaurantSummary(Long id, String name, String slug, RestaurantStatus status, Instant createdAt,
                                    List<OwnerSummary> owners) {
    }

    /** {@code slug} is optional; when blank it's derived from {@code restaurantName}. */
    public record OnboardRestaurantRequest(
            @NotBlank @Size(max = 100) String restaurantName,
            @Size(max = 60) String slug,
            @Size(max = 80) String ownerDisplayName) {
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
