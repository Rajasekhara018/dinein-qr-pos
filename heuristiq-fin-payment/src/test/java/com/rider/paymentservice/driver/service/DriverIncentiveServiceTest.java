package com.heuristiq.paymentservice.driver.service;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.driver.model.DriverIncentive;
import com.heuristiq.paymentservice.driver.repository.DriverIncentiveRepository;
import com.heuristiq.paymentservice.driver.repository.DriverPaymentRepository;
import com.heuristiq.paymentservice.driver.service.incentive.TripCountIncentiveRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported trigger condition (Rider_BookingsAPI's TripCountIncentiveRule, verbatim thresholds):
 * 10/20/30 trips-this-week -> 100/250/500 bonus. Real trip count comes from this service's own
 * driver_payments rows (see DriverIncentiveContext's javadoc for why HIGH_RATING/ZERO_CANCELLATION
 * aren't ported: no rating/cancellation data source here).
 */
class DriverIncentiveServiceTest {

    private DriverIncentiveRepository incentiveRepository;
    private DriverPaymentRepository driverPaymentRepository;
    private DriverIncentiveService service;

    @BeforeEach
    void setUp() {
        incentiveRepository = mock(DriverIncentiveRepository.class);
        driverPaymentRepository = mock(DriverPaymentRepository.class);
        service = new DriverIncentiveService(incentiveRepository, driverPaymentRepository,
                List.of(new TripCountIncentiveRule()), new com.heuristiq.paymentservice.config.MoneyProperties());

        when(incentiveRepository.save(any(DriverIncentive.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void awardsTripCountBonus_whenTripsThisWeekMeetsThreshold() {
        when(driverPaymentRepository.countByDriverIdAndCreatedAtBetween(eq("driver-1"), any(), any()))
                .thenReturn(25L);
        when(incentiveRepository.findByDriverIdAndWeekStartDate(eq("driver-1"), any(LocalDate.class)))
                .thenReturn(List.of());

        List<DriverIncentive> awarded = service.calculateAndAwardIncentives("driver-1");

        assertThat(awarded).hasSize(1);
        DriverIncentive incentive = awarded.get(0);
        assertThat(incentive.getRuleName()).isEqualTo("TRIP_COUNT");
        assertThat(incentive.getBonusAmount()).isEqualByComparingTo("250.0"); // 20 <= 25 < 30
        assertThat(incentive.getBonusAmountCurrency()).isEqualTo("INR");
        assertThat(incentive.getWeekStartDate().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        verify(incentiveRepository, times(1)).save(any(DriverIncentive.class));
    }

    @Test
    void doesNotAward_whenTripsBelowThreshold() {
        when(driverPaymentRepository.countByDriverIdAndCreatedAtBetween(eq("driver-2"), any(), any()))
                .thenReturn(5L);
        when(incentiveRepository.findByDriverIdAndWeekStartDate(eq("driver-2"), any(LocalDate.class)))
                .thenReturn(List.of());

        List<DriverIncentive> awarded = service.calculateAndAwardIncentives("driver-2");

        assertThat(awarded).isEmpty();
        verify(incentiveRepository, never()).save(any(DriverIncentive.class));
    }

    @Test
    void doesNotDoubleAward_sameRuleSameWeek() {
        when(driverPaymentRepository.countByDriverIdAndCreatedAtBetween(eq("driver-3"), any(), any()))
                .thenReturn(15L);
        DriverIncentive alreadyAwarded = DriverIncentive.builder()
                .id("existing-1")
                .driverId("driver-3")
                .ruleName("TRIP_COUNT")
                .bonusAmount(new BigDecimal("100.0"))
                .build();
        when(incentiveRepository.findByDriverIdAndWeekStartDate(eq("driver-3"), any(LocalDate.class)))
                .thenReturn(List.of(alreadyAwarded));

        List<DriverIncentive> awarded = service.calculateAndAwardIncentives("driver-3");

        assertThat(awarded).isEmpty();
        verify(incentiveRepository, never()).save(any(DriverIncentive.class));
    }

    @Test
    void getIncentives_delegatesToRepository() {
        DriverIncentive incentive = DriverIncentive.builder().id("i1").driverId("driver-4").build();
        when(incentiveRepository.findByDriverIdOrderByAwardedAtDesc("driver-4")).thenReturn(List.of(incentive));

        List<DriverIncentive> result = service.getIncentives("driver-4");

        assertThat(result).containsExactly(incentive);
        verify(driverPaymentRepository, never()).countByDriverIdAndCreatedAtBetween(anyString(), any(), any());
    }
}
