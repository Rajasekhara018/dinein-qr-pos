package com.heuristiq.payment.wallet.controller;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.payment.security.AuthPrincipal;
import com.heuristiq.payment.shared.exception.GlobalExceptionHandler;
import com.heuristiq.payment.wallet.service.WalletBalanceService;
import com.heuristiq.payment.wallet.service.WalletTopupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The deprecated {@code GET /{customerId}/balance} pass-through: same caller rule as before (the customer or an
 * authorised admin), the JSON shape the installed Customer apps parse, 503 (never 0) when the wallet is down.
 */
class WalletBalancePassThroughTest {

    private static final String PATH = "/api/v1/payment/wallet/cust-1/balance";

    private WalletBalanceService balances;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        balances = mock(WalletBalanceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WalletController(mock(WalletTopupService.class), balances))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String userId, String role, boolean elevated) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(userId, role, elevated), null, List.of()));
    }

    @Test
    void ownerGetsTheShapeTheAppParses_withADeprecationHeader() throws Exception {
        loginAs("cust-1", "CUSTOMER", false);
        when(balances.balanceOf("cust-1")).thenReturn(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("125.50"), "INR"));

        // {status, object:{balance:{code, value}}}: the apps read the amount object
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.object.balance.code").value("INR"))
                .andExpect(jsonPath("$.object.balance.value").value(125.5))
                .andExpect(jsonPath("$.object.balance.value").isNumber());
    }

    @Test
    void anotherCustomerIsForbidden_andTheWalletIsNeverAsked() throws Exception {
        loginAs("cust-2", "CUSTOMER", false);

        mvc.perform(get(PATH)).andExpect(status().isForbidden());

        verifyNoInteractions(balances);
    }

    @Test
    void anAuthorisedAdminAndTheServiceIdentityMayRead() throws Exception {
        when(balances.balanceOf("cust-1")).thenReturn(com.heuristiq.payment.shared.money.Money.of(BigDecimal.ZERO, "INR"));
        loginAs("admin-1", "ADMIN", true);
        mvc.perform(get(PATH)).andExpect(status().isOk());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthPrincipal.SYSTEM, null, List.of()));
        mvc.perform(get(PATH)).andExpect(status().isOk());
    }

    @Test
    void anAdminWithoutElevatedAccessIsForbidden() throws Exception {
        loginAs("admin-1", "ADMIN", false);

        mvc.perform(get(PATH)).andExpect(status().isForbidden());

        verifyNoInteractions(balances);
    }

    @Test
    void walletDown_is503_neverZeroOrEmpty() throws Exception {
        loginAs("cust-1", "CUSTOMER", false);
        when(balances.balanceOf("cust-1")).thenThrow(new DownstreamUnavailableException("wallet-service",
                DownstreamUnavailableException.Reason.CIRCUIT_OPEN, null));

        mvc.perform(get(PATH)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.object").doesNotExist());
        verify(balances).balanceOf("cust-1");
    }
}
