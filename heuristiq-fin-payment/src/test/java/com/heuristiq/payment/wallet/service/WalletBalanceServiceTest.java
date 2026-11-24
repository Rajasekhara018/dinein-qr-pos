package com.heuristiq.payment.wallet.service;

import com.heuristiq.payment.security.AuthPrincipal;
import com.heuristiq.payment.wallet.port.WalletBalanceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The balance pass-through reads in the caller's organization and returns exactly what the wallet says. */
class WalletBalanceServiceTest {

    private final WalletBalanceClient client = mock(WalletBalanceClient.class);
    private final WalletBalanceService service = new WalletBalanceService(client);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsInTheSessionOrganization() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal("cust-1", "CUSTOMER", false, "org-a"), null, List.of()));
        when(client.balanceOf("cust-1", "org-a")).thenReturn(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("10.00"), "INR"));

        assertThat(service.balanceOf("cust-1").value()).isEqualByComparingTo("10.00");
        verify(client).balanceOf("cust-1", "org-a");
    }

    @Test
    void aCallerWithoutAnOrganizationSendsNone() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal("cust-1", "CUSTOMER", false), null, List.of()));
        when(client.balanceOf("cust-1", null)).thenReturn(com.heuristiq.payment.shared.money.Money.of(BigDecimal.ONE, "INR"));

        assertThat(service.balanceOf("cust-1").value()).isEqualByComparingTo("1");
    }
}
