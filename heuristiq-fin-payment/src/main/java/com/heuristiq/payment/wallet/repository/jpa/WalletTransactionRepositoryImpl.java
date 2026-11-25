package com.heuristiq.payment.wallet.repository.jpa;

import com.heuristiq.payment.wallet.model.WalletTransaction;
import com.heuristiq.payment.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/** JPA/Postgres adapter for {@link WalletTransactionRepository}. */
@Repository
@RequiredArgsConstructor
class WalletTransactionRepositoryImpl implements WalletTransactionRepository {

    private final JpaWalletTransactionRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public Optional<WalletTransaction> findByRazorpayOrderId(String razorpayOrderId) {
        return jpaRepository.findByRazorpayOrderId(razorpayOrderId);
    }

    @Override
    public List<WalletTransaction> findByUserIdAndStatus(String userId, String status) {
        return jpaRepository.findByUserIdAndStatus(userId, status);
    }

    @Override
    public Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public BigDecimal sumLegacyRefunds(String paymentId) {
        BigDecimal sum = jpaRepository.sumLegacyRefunds(paymentId);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
