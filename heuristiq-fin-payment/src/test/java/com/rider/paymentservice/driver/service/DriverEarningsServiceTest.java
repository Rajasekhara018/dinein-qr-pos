package com.heuristiq.paymentservice.driver.service;

import com.heuristiq.paymentservice.driver.dto.DriverEarningsResponse;
import com.heuristiq.paymentservice.driver.model.DriverPayment;
import com.heuristiq.paymentservice.driver.model.DriverSplitStatus;
import com.heuristiq.paymentservice.driver.repository.DriverPaymentRepository;
import com.heuristiq.paymentservice.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Driver payment records are history only: Payment no longer writes them (the driver's share is booked in Rider_WalletService),
 * and the old rows stay readable, each organization seeing only its own.
 */
class DriverEarningsServiceTest {

    private final DriverPaymentRepository repository = mock(DriverPaymentRepository.class);
    private final DriverEarningsService service = new DriverEarningsService(repository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String tenantId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal("driver-1", "DRIVER", false, tenantId), null, List.of()));
    }

    private static DriverPayment row(String id, String tenantId) {
        return DriverPayment.builder().id(id).driverId("driver-1").rideId("ride-1").tenantId(tenantId)
                .amount(new BigDecimal("200.00")).amountCurrency("INR").splitStatus(DriverSplitStatus.SPLIT).build();
    }

    @Test
    void theDriversOldRecordsAreListedForTheirOrganizationOnly() {
        DriverPayment mine = row("a", "org-a");
        DriverPayment theirs = row("b", "org-b");
        when(repository.findByDriverIdOrderByCreatedAtDesc("driver-1")).thenReturn(List.of(mine, theirs));
        as("org-a");

        assertThat(service.getEarnings("driver-1")).containsExactly(mine);
    }

    @Test
    void oneRidesRecordsAreFilteredTheSameWay() {
        DriverPayment mine = row("a", "org-a");
        DriverPayment theirs = row("b", "org-b");
        when(repository.findByDriverIdAndRideId("driver-1", "ride-1")).thenReturn(List.of(theirs, mine));
        as("org-b");

        assertThat(service.getEarningsForRide("driver-1", "ride-1")).containsExactly(theirs);
    }

    @Test
    void theServiceOffersNoWayToWriteARecord() {
        assertThat(Arrays.stream(DriverPaymentRepository.class.getMethods()).map(m -> m.getName()))
                .noneMatch(name -> name.startsWith("save") || name.startsWith("delete") || name.startsWith("update"));
        assertThat(Arrays.stream(DriverEarningsService.class.getDeclaredMethods()).map(m -> m.getName()))
                .doesNotContain("computeEarnings", "completePendingSplits");
    }

    @Test
    void theApiShowsAPendingRecordWithNoInventedAmounts() {
        DriverPayment pending = row("p", "org-a");
        pending.setSplitStatus(DriverSplitStatus.PENDING_SPLIT);

        DriverEarningsResponse response = DriverEarningsResponse.from(pending);

        assertThat(response.splitStatus()).isEqualTo(DriverSplitStatus.PENDING_SPLIT);
        assertThat(response.platformFee()).isNull();
        assertThat(response.netAmount()).isNull();
        assertThat(DriverEarningsResponse.from(DriverPayment.builder().build()).splitStatus())
                .as("a row written before V25").isEqualTo(DriverSplitStatus.SPLIT);
    }
}
