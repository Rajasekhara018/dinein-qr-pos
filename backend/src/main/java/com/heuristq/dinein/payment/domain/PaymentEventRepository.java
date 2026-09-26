package com.heuristq.dinein.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, Long> {

    Optional<PaymentEventEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
