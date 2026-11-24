package com.heuristiq.paymentservice.wallet.repository.jpa;

import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.wallet.model.WalletTopupSession;
import com.heuristiq.paymentservice.wallet.model.WalletTopupStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA binding for {@code wallet_topup_sessions}. Not injected directly — see {@link WalletTopupSessionRepositoryImpl}. */
interface JpaWalletTopupSessionRepository extends JpaRepository<WalletTopupSession, String> {

    Optional<WalletTopupSession> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletTopupSession> findByProviderOrderId(String providerOrderId);

    List<WalletTopupSession> findByStatusInAndGatewayIn(List<WalletTopupStatus> statuses, List<PaymentGatewayType> gateways);

    @Query("SELECT s FROM WalletTopupSession s WHERE s.status = :status "
            + "AND (s.creditNextAttemptAt IS NULL OR s.creditNextAttemptAt <= :now) ORDER BY s.updatedAt ASC")
    List<WalletTopupSession> findDue(@Param("status") WalletTopupStatus status, @Param("now") java.time.LocalDateTime now,
                                     Pageable page);
}
