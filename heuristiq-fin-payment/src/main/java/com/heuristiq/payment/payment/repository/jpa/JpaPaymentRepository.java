package com.heuristiq.payment.payment.repository.jpa;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA binding for {@code payments}. Not injected directly — see {@link PaymentRepositoryImpl}. */
interface JpaPaymentRepository extends JpaRepository<Payment, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.internalPaymentId = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") String id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime threshold);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND p.gateway IN :gateways "
            + "AND p.internalPaymentId > :afterId ORDER BY p.internalPaymentId")
    List<Payment> findPollCandidatesAfter(@Param("statuses") List<PaymentStatus> statuses,
                                          @Param("gateways") List<PaymentGatewayType> gateways,
                                          @Param("afterId") String afterId, Pageable page);
}
