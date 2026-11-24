package com.heuristiq.paymentservice.payment.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import jakarta.persistence.Column;
import com.heuristiq.paymentservice.payment.lifecycle.PaymentLifecycleListener;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * The payment-state-machine record, per HLD §17.1/§17.2. Distinct from — and the
 * anchor that — wallet_transactions / driver_payments hang off of: those record the
 * customer/driver cash-flow *effects* of a payment, this record tracks the payment
 * itself as it moves INITIATED -> AUTHORIZED -> CAPTURED -> SETTLED (or one of the
 * failure terminal states). Does not exist verbatim in the monolith's schema — the
 * monolith conflates payment with the cash-flow tables — so this is a clean design
 * for this service, per the task's HLD §17.1 field list plus a few operational
 * fields (method, provider_order_id, failure_reason, updated_at) needed to actually
 * drive the state machine.
 */
@Entity
@EntityListeners(PaymentLifecycleListener.class)
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "internal_payment_id")
    private String internalPaymentId;

    /** Razorpay payment id (populated once the provider authorizes/captures). Null for CASH and while PENDING online. */
    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    /** Razorpay order id, created up-front for RAZORPAY payments so the webhook can be matched back to this record. */
    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "ride_id")
    private String rideId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "driver_id")
    private String driverId;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    private String currency = PaymentCommonConstants.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    /**
     * Which of the 4 supported gateways this payment was routed through — RAZORPAY, PAYU, TOUCAN,
     * or PINELABS. Null for CASH (no gateway is ever involved in a cash payment).
     *
     * Relationship to {@link #method}: {@code method} is kept as the pre-existing CASH/RAZORPAY
     * enum for backward compatibility — every existing Ride/Dispatch-service caller already sends
     * {@code method: "RAZORPAY"} for online payments, and renaming/widening that enum would break
     * them. Rather than repurpose {@code method} to enumerate all 4 gateways, {@code method}'s
     * RAZORPAY value is now read as "online / gateway-routed" in general, and this separate
     * {@code gateway} field is what actually selects the provider for that case — defaulting to
     * RAZORPAY when the caller (POST /payments) doesn't specify one, so existing callers see
     * byte-identical behavior. A caller wanting PayU/Toucan/PineLabs instead sends
     * {@code method: "RAZORPAY", gateway: "PAYU"} (etc.) — see CreatePaymentRequest.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gateway")
    private PaymentGatewayType gateway;

    /** Caller-supplied idempotency key for POST /payments — dedupes duplicate create-payment retries. */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /** Reason captured when status transitions to FAILED/CANCELLED/EXPIRED. */
    /** Payer details entered at checkout; null for payments created by other services. */
    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_mobile")
    private String customerMobile;

    /** Set when another Rider application (not a ride) raised this payment, e.g. "matrimonial-api". */
    @Column(name = "source_service")
    private String sourceService;

    /** That application's own id for what is being paid for (a subscription, a credit pack); opaque here. */
    @Column(name = "source_reference")
    private String sourceReference;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Cash-payment invoice number, set when a CASH payment is captured (see InvoiceService in the monolith). */
    @Column(name = "invoice_number")
    private String invoiceNumber;

    /**
     * Real receipt number, generated once (in {@code onPaymentCaptured}) for every captured
     * payment regardless of method — see {@link com.heuristiq.paymentservice.payment.dto.ReceiptResponse}.
     * Distinct from {@link #invoiceNumber}, which is a monolith-ported, CASH-only field kept for
     * backward compatibility.
     */
    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "receipt_generated_at")
    private LocalDateTime receiptGeneratedAt;

    /** Set by {@code SettlementScheduler}/{@code PaymentService#settleOldCapturedPayments} when this payment transitions CAPTURED -> SETTLED. */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
