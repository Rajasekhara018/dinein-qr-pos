package com.heuristiq.payment.driver.dto;

import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.driver.model.DriverIncentive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public record DriverIncentiveResponse(
        String id,
        String driverId,
        String ruleName,
        String description,
        Money bonusAmount,
        LocalDate weekStartDate,
        LocalDateTime awardedAt,
        String status
) {
    public static DriverIncentiveResponse from(DriverIncentive i) {
        return new DriverIncentiveResponse(i.getId(), i.getDriverId(), i.getRuleName(), i.getDescription(),
                Money.ofNullable(i.getBonusAmount(), i.getBonusAmountCurrency()), i.getWeekStartDate(), i.getAwardedAt(), i.getStatus());
    }
}
