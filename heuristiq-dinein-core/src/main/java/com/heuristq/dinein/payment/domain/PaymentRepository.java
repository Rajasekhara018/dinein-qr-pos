package com.heuristq.dinein.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByProviderAndProviderOrderId(String provider, String providerOrderId);

    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    List<PaymentEntity> findByOrderIdOrderByIdAsc(Long orderId);

    List<PaymentEntity> findByOrderIdIn(Collection<Long> orderIds);
}
