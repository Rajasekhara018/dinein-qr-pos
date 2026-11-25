package com.heuristiq.payment.wallet.repository;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.wallet.model.WalletTopupSession;
import com.heuristiq.payment.wallet.model.WalletTopupStatus;

import java.util.List;
import java.util.Optional;

/**
 * Database-agnostic persistence port for {@code wallet_topup_sessions}. Implementations
 * (e.g. {@code jpa.WalletTopupSessionRepositoryImpl}) own the storage-specific details.
 */
public interface WalletTopupSessionRepository {

    WalletTopupSession save(WalletTopupSession session);

    Optional<WalletTopupSession> findById(String sessionId);

    Optional<WalletTopupSession> findByIdempotencyKey(String idempotencyKey);

    Optional<WalletTopupSession> findByProviderOrderId(String providerOrderId);

    /**
     * WalletTopupStatusPollingScheduler: still-open sessions routed through one of the
     * non-webhook-driven gateways (PAYU/TOUCAN/PINELABS — RAZORPAY stays webhook-driven via
     * handleTopupWebhook, mirroring PaymentService's own poll/webhook split).
     */
    List<WalletTopupSession> findByStatusInAndGatewayIn(List<WalletTopupStatus> statuses, List<PaymentGatewayType> gateways);

    /**
     * Paid top-ups whose wallet credit is still open and due: status PAID_AWAITING_CREDIT and a next-attempt time that is
     * unset or not after {@code now}, oldest first, at most {@code limit} rows.
     */
    List<WalletTopupSession> findCreditDue(java.time.LocalDateTime now, int limit);
}
