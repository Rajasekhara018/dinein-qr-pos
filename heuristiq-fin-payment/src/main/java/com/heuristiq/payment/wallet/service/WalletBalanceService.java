package com.heuristiq.paymentservice.wallet.service;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.security.TenantAccess;
import com.heuristiq.paymentservice.wallet.port.WalletBalanceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Reads a customer's balance from Rider_WalletService on behalf of the deprecated Payment balance endpoint. Payment
 * keeps no balance of its own; the organization read is the caller's session organization.
 */
@Service
@RequiredArgsConstructor
public class WalletBalanceService {

    private final WalletBalanceClient walletBalanceClient;

    /**
     * @return the customer's spendable balance in the wallet
     * @throws com.heuristiq.resilient.DownstreamUnavailableException when the wallet is unavailable (HTTP 503)
     */
    public Money balanceOf(String customerId) {
        return walletBalanceClient.balanceOf(customerId, TenantAccess.stamp());
    }
}
