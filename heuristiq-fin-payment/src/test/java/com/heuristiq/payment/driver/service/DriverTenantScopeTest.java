package com.heuristiq.payment.driver.service;

import com.heuristiq.payment.driver.model.DriverPayment;
import com.heuristiq.payment.driver.model.DriverPayout;
import com.heuristiq.payment.driver.repository.DriverPaymentRepository;
import com.heuristiq.payment.driver.repository.DriverPayoutRepository;
import com.heuristiq.payment.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A driver's earnings and payouts belong to the organization they were created in. */
class DriverTenantScopeTest {

    private DriverPaymentRepository earningsRepository;
    private DriverPayoutRepository payoutRepository;
    private DriverEarningsService earnings;
    private DriverPayoutService payouts;

    @BeforeEach
    void setUp() {
        earningsRepository = mock(DriverPaymentRepository.class);
        payoutRepository = mock(DriverPayoutRepository.class);
        earnings = new DriverEarningsService(earningsRepository);
        payouts = new DriverPayoutService(payoutRepository, new com.heuristiq.payment.config.MoneyProperties());
        when(payoutRepository.save(any(DriverPayout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void otherOrganizationsEarningsRowsAreNotListed() {
        DriverPayment mine = DriverPayment.builder().tenantId("org-a").build();
        DriverPayment theirs = DriverPayment.builder().tenantId("org-b").build();
        DriverPayment legacy = DriverPayment.builder().build();
        when(earningsRepository.findByDriverIdOrderByCreatedAtDesc("driver-1")).thenReturn(List.of(mine, theirs, legacy));
        as(new AuthPrincipal("driver-1", "DRIVER", false, "org-a"));

        assertThat(earnings.getEarnings("driver-1")).containsExactly(mine);
    }

    @Test
    void payoutsAreStampedAndFilteredTheSameWay() {
        as(new AuthPrincipal("driver-1", "DRIVER", false, "org-a"));
        DriverPayout stored = payouts.requestPayout("driver-1", new com.heuristiq.payment.driver.dto.DriverPayoutRequestDto(
                com.heuristiq.payment.shared.money.Money.of(new BigDecimal("50.00"), "INR"), "Asha", "123456789", "HDFC0001234", "HDFC"));
        assertThat(stored.getTenantId()).isEqualTo("org-a");
        assertThat(stored.getRequestedAmount()).isEqualByComparingTo("50.00");
        assertThat(stored.getRequestedAmountCurrency()).isEqualTo("INR");

        DriverPayout theirs = DriverPayout.builder().tenantId("org-b").build();
        when(payoutRepository.findByDriverIdOrderByRequestedAtDesc("driver-1")).thenReturn(List.of(stored, theirs));

        assertThat(payouts.getPayouts("driver-1")).containsExactly(stored);
    }
}
