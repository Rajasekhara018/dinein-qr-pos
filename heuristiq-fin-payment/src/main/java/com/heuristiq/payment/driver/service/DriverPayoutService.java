package com.heuristiq.payment.driver.service;

import java.math.BigDecimal;
import com.heuristiq.payment.shared.money.MoneyRequests;
import com.heuristiq.payment.config.MoneyProperties;
import com.heuristiq.payment.security.TenantAccess;
import com.heuristiq.payment.shared.money.AmountCurrency;
import com.heuristiq.payment.driver.dto.DriverPayoutRequestDto;
import com.heuristiq.payment.driver.model.DriverPayout;
import com.heuristiq.payment.driver.repository.DriverPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverPayoutService {

    private final DriverPayoutRepository driverPayoutRepository;
    private final MoneyProperties moneyProperties;

    public DriverPayout requestPayout(String driverId, DriverPayoutRequestDto req) {
        BigDecimal amount = MoneyRequests.valueIn(req.amount(), moneyProperties.getCurrency());
        DriverPayout payout = DriverPayout.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(TenantAccess.stamp())
                .driverId(driverId)
                .requestedAmount(amount)
                .requestedAmountCurrency(AmountCurrency.of(amount, moneyProperties.getCurrency()))
                .status("PENDING")
                .accountHolderName(req.accountHolderName())
                .accountNumber(req.accountNumber())
                .ifscCode(req.ifscCode())
                .bankName(req.bankName())
                .requestedAt(LocalDateTime.now())
                .build();
        DriverPayout saved = driverPayoutRepository.save(payout);
        log.info("Payout requested: driverId={} amount={}", driverId, amount);
        return saved;
    }

    public List<DriverPayout> getPayouts(String driverId) {
        return driverPayoutRepository.findByDriverIdOrderByRequestedAtDesc(driverId).stream()
                .filter(row -> TenantAccess.canSee(row.getTenantId())).toList();
    }
}
