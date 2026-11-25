package com.heuristiq.payment.payment.repository;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Database-agnostic persistence port for {@code payments}. Implementations
 * (e.g. {@code jpa.PaymentRepositoryImpl}) own the storage-specific details.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(String paymentId);

    /** Loads the payment holding a database write lock until the current transaction ends. */
    Optional<Payment> findByIdForUpdate(String paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    /** Settlement job: CAPTURED payments whose last state change is older than the settlement delay threshold. */
    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime threshold);

    /**
     * Payment-status-polling job (PaymentStatusPollingScheduler): one page of still-open payments
     * routed through one of the polled gateways, in payment-id order, starting after
     * {@code afterPaymentId} (keyset paging; pass an empty string for the first page). CASH rows have
     * gateway=null and are naturally excluded by the IN clause.
     */
    List<Payment> findPollCandidatesAfter(List<PaymentStatus> statuses, List<PaymentGatewayType> gateways,
                                          String afterPaymentId, int limit);
}
