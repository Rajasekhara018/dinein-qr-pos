package com.heuristiq.payment.wallet.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The legacy refund total against a real (H2) database. Balances, top-ups, ride debits and refund credits are
 * Rider_WalletService's and have no code here any more; only the total the old table recorded before refund references
 * existed still limits what a payment can be refunded.
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private JdbcTemplate jdbc;

    private void legacyRow(String customer, String payment, String amount, String source, String key) {
        jdbc.update("INSERT INTO wallet_transactions (id, user_id, amount, type, source, reference_id, status, idempotency_key, created_at) "
                + "VALUES (?, ?, ?, 'CREDIT', ?, ?, 'SUCCESS', ?, CURRENT_TIMESTAMP)", UUID.randomUUID().toString(), customer,
                new java.math.BigDecimal(amount), source, payment, key);
    }

    @Test
    void legacyRefundTotal_countsOnlyKeylessRefundRowsOfThePayment() {
        String customer = "cust-" + UUID.randomUUID();
        String payment = "pay-legacy-" + customer;
        jdbc.update("INSERT INTO wallet_accounts (user_id, created_at) VALUES (?, CURRENT_TIMESTAMP)", customer);
        legacyRow(customer, payment, "40", "REFUND", null);                          // written before references existed
        legacyRow(customer, payment, "10", "REFUND", "REFUND:ref-" + customer);       // keyed: also in payment_refunds
        legacyRow(customer, payment, "5", "TOPUP", null);                             // not a refund
        legacyRow(customer, "another-payment", "7", "REFUND", null);                  // another payment

        assertThat(walletService.getLegacyRefundTotal(payment)).isEqualByComparingTo("40.00");
    }

    @Test
    void aPaymentWithNoLegacyRefundsHasATotalOfZero() {
        assertThat(walletService.getLegacyRefundTotal("pay-none-" + UUID.randomUUID())).isEqualByComparingTo("0.00");
    }
}
