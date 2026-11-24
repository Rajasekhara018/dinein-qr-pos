package com.heuristiq.paymentservice.splitfare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSplitFareRequest(
        @NotBlank String rideId,
        @NotBlank String initiatorUserId,
        @NotEmpty List<SplitMemberDto> members
) {
}
