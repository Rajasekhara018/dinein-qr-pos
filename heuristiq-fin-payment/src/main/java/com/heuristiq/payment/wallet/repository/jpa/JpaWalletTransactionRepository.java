package com.heuristiq.payment.wallet.repository.jpa;

import com.heuristiq.payment.wallet.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/** Spring Data JPA binding for {@code wallet_transactions}. Not injected directly — see {@link WalletTransactionRepositoryImpl}. */
interface JpaWalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    Optional<WalletTransaction> findByRazorpayOrderId(String razorpayOrderId);

    List<WalletTransaction> findByUserIdAndStatus(String userId, String status);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t WHERE t.source = 'REFUND' "
            + "AND t.type = 'CREDIT' AND t.status = 'SUCCESS' AND t.referenceId = :paymentId "
            + "AND t.idempotencyKey IS NULL")
    BigDecimal sumLegacyRefunds(@Param("paymentId") String paymentId);
}
