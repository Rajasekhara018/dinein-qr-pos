package com.heuristiq.payment.driver.dto;

import com.heuristiq.payment.shared.money.PositiveMoney;
import com.heuristiq.payment.shared.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;

public record DriverPayoutRequestDto(
        @NotNull @PositiveMoney Money amount,
        @NotBlank String accountHolderName,
        @NotBlank String accountNumber,
        @NotBlank String ifscCode,
        String bankName
) {
}
