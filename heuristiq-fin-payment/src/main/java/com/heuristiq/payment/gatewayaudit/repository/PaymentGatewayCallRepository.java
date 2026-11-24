package com.heuristiq.payment.gatewayaudit.repository;

import com.heuristiq.payment.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.payment.gatewayaudit.model.PaymentGatewayCall;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence for {@code payment_gateway_calls}. */
public interface PaymentGatewayCallRepository extends JpaRepository<PaymentGatewayCall, String> {

    /** Calls already logged for a payment and operation, used to number the next attempt. */
    long countByPaymentIdAndOperation(String paymentId, GatewayCallOperation operation);

    /** Oldest first; always bounded by the pageable. */
    List<PaymentGatewayCall> findByPaymentIdOrderByCreatedAtAscIdAsc(String paymentId, Pageable pageable);
}
