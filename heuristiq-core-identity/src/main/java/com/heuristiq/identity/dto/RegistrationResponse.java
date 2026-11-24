package com.heuristiq.uam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationResponse(
        String message,
        String userId,
        @JsonProperty("driver_id") String driverIdSnake,
        String driverId,
        String onboardingStatus,
        String preferredPaymentMethod,
        Boolean isDriverOnline
) {
}
