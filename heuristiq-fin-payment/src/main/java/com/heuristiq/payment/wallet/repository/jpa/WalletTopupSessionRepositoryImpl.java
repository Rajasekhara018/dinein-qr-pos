package com.heuristiq.paymentservice.wallet.repository.jpa;

import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.wallet.model.WalletTopupSession;
import com.heuristiq.paymentservice.wallet.model.WalletTopupStatus;
import com.heuristiq.paymentservice.wallet.repository.WalletTopupSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** JPA/Postgres adapter for {@link WalletTopupSessionRepository}. */
@Repository
@RequiredArgsConstructor
class WalletTopupSessionRepositoryImpl implements WalletTopupSessionRepository {

    private final JpaWalletTopupSessionRepository jpaRepository;

    @Override
    public WalletTopupSession save(WalletTopupSession session) {
        return jpaRepository.save(session);
    }

    @Override
    public Optional<WalletTopupSession> findById(String sessionId) {
        return jpaRepository.findById(sessionId);
    }

    @Override
    public Optional<WalletTopupSession> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<WalletTopupSession> findByProviderOrderId(String providerOrderId) {
        return jpaRepository.findByProviderOrderId(providerOrderId);
    }

    @Override
    public List<WalletTopupSession> findByStatusInAndGatewayIn(List<WalletTopupStatus> statuses, List<PaymentGatewayType> gateways) {
        return jpaRepository.findByStatusInAndGatewayIn(statuses, gateways);
    }

    @Override
    public List<WalletTopupSession> findCreditDue(java.time.LocalDateTime now, int limit) {
        return jpaRepository.findDue(WalletTopupStatus.PAID_AWAITING_CREDIT, now,
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
    }
}
