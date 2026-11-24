package com.heuristiq.paymentservice.splitfare.dto;

import com.heuristiq.phone.validation.ValidPhone;
import jakarta.validation.constraints.NotBlank;

public record SplitMemberDto(
        @NotBlank @ValidPhone String phoneNumber,
        String name
) {
}
