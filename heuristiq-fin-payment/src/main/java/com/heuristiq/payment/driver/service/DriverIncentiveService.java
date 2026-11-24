package com.heuristiq.paymentservice.driver.service;

import com.heuristiq.paymentservice.config.MoneyProperties;
import com.heuristiq.paymentservice.security.TenantAccess;
import com.heuristiq.paymentservice.shared.money.AmountCurrency;
import com.heuristiq.paymentservice.driver.model.DriverIncentive;
import com.heuristiq.paymentservice.driver.repository.DriverIncentiveRepository;
import com.heuristiq.paymentservice.driver.repository.DriverPaymentRepository;
import com.heuristiq.paymentservice.driver.service.incentive.DriverIncentiveContext;
import com.heuristiq.paymentservice.driver.service.incentive.IncentiveRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ported from Rider_BookingsAPI's {@code driver.incentive.DriverIncentiveService}. Same
 * trigger/award logic shape: build a context for the current ISO week (Monday start), skip any
 * rule already awarded to this driver for this week, award every eligible rule otherwise.
 *
 * Real difference from the monolith (see DriverIncentiveContext's javadoc): {@code tripsThisWeek}
 * comes from counting this service's own {@code driver_payments} rows (one row per ride whose
 * payment reached CAPTURED, written until the accounting rebuild; Payment no longer adds rows, so trips after it are not
 * counted here until the rule reads Rider_WalletService) created since this week's
 * Monday, instead of the monolith's Redis counter. Only TRIP_COUNT is ported; HIGH_RATING and
 * ZERO_CANCELLATION are not (no rating/cancellation data source here).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverIncentiveService {

    private final DriverIncentiveRepository incentiveRepository;
    private final DriverPaymentRepository driverPaymentRepository;
    private final List<IncentiveRule> rules;
    private final MoneyProperties moneyProperties;

    @Transactional
    public List<DriverIncentive> calculateAndAwardIncentives(String driverId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime weekStartAt = weekStart.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long tripsThisWeek = driverPaymentRepository.countByDriverIdAndCreatedAtBetween(driverId, weekStartAt, now);

        DriverIncentiveContext ctx = new DriverIncentiveContext(driverId, (int) tripsThisWeek, weekStart);

        Set<String> alreadyAwardedRules = incentiveRepository.findByDriverIdAndWeekStartDate(driverId, weekStart)
                .stream()
                .map(DriverIncentive::getRuleName)
                .collect(Collectors.toSet());

        List<DriverIncentive> newlyAwarded = new ArrayList<>();
        for (IncentiveRule rule : rules) {
            if (alreadyAwardedRules.contains(rule.getRuleName())) {
                continue;
            }
            if (!rule.isEligible(ctx)) {
                continue;
            }
            BigDecimal bonus = rule.calculateBonus(ctx);
            DriverIncentive incentive = DriverIncentive.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(TenantAccess.stamp())
                    .driverId(driverId)
                    .ruleName(rule.getRuleName())
                    .description(rule.getDescription())
                    .bonusAmount(bonus)
                    .bonusAmountCurrency(AmountCurrency.of(bonus, moneyProperties.getCurrency()))
                    .weekStartDate(weekStart)
                    .awardedAt(now)
                    .status("PENDING")
                    .build();
            DriverIncentive saved = incentiveRepository.save(incentive);
            newlyAwarded.add(saved);
            log.info("Awarded incentive {} to driver {}: bonus={} tripsThisWeek={}",
                    rule.getRuleName(), driverId, incentive.getBonusAmount(), tripsThisWeek);
        }
        return newlyAwarded;
    }

    public List<DriverIncentive> getIncentives(String driverId) {
        return incentiveRepository.findByDriverIdOrderByAwardedAtDesc(driverId).stream()
                .filter(row -> TenantAccess.canSee(row.getTenantId())).toList();
    }
}
