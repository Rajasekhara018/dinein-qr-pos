package com.heuristiq.payment.gatewayaudit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One outbound call to a payment gateway (create order, refund, status check). Holds metadata only:
 * never a request or response body, credential or card data. {@code createdAt} is UTC.
 */
@Entity
@Table(name = "payment_gateway_calls")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayCall {

    @Id
    private String id;

    @Column(nullable = false, length = 32)
    private String gateway;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GatewayCallOperation operation;

    @Column(name = "payment_id", length = 255)
    private String paymentId;

    /** Refund reference for refunds, the payment idempotency key for order creation, the order id for status checks. */
    @Column(length = 128)
    private String reference;

    /** The gateway's own identifier (order id, refund id, payment id) when known. */
    @Column(name = "gateway_ref", length = 255)
    private String gatewayRef;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_class", length = 128)
    private String errorClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GatewayCallOutcome outcome;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
