package com.heuristiq.payment.wallet.service;

import com.heuristiq.payment.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.heuristiq.payment.wallet.service.WalletConstants.MONEY_SCALE;

/**
 * Read-only access to the legacy {@code wallet_transactions} table.
 *
 * <p>Rider_WalletService owns every user balance and ledger entry: top-ups, balance reads, the transaction list and
 * refund credits (see {@code PaymentRefundService}) all go there, and nothing writes the legacy table any more. What is
 * left is the total of refunds that table recorded before refund references existed ({@link #getLegacyRefundTotal}),
 * which still counts against what a payment can be refunded.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletTransactionRepository walletTransactionRepository;

    /** Refund credits for the payment written before refund references existed; see the refund ledger. */
    @Transactional(readOnly = true)
    public BigDecimal getLegacyRefundTotal(String paymentId) {
        return round2(walletTransactionRepository.sumLegacyRefunds(paymentId));
    }

    private static BigDecimal round2(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
