package com.heuristiq.payment.wallet.repository;

import com.heuristiq.payment.wallet.model.WalletTransaction;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * Database-agnostic persistence port for {@code wallet_transactions}. Implementations
 * (e.g. {@code jpa.WalletTransactionRepositoryImpl}) own the storage-specific details.
 */
public interface WalletTransactionRepository {

    WalletTransaction save(WalletTransaction transaction);

    Optional<WalletTransaction> findByRazorpayOrderId(String razorpayOrderId);

    List<WalletTransaction> findByUserIdAndStatus(String userId, String status);

    /** The posting made for this business-event key, if any. */
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Refund credits recorded for the payment before refund references existed (no idempotency key).
     * Counted as already refunded so history written by the old refund code still limits new refunds.
     */
    BigDecimal sumLegacyRefunds(String paymentId);
}
