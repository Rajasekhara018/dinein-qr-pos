package com.heuristq.dinein.payment.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * One provider-side order for one of our orders. Providers that allow retries on the same order (Razorpay) keep one
 * row; others (PayU) get a new row per attempt.
 */
@Getter
@Setter
@Entity
@Table(name = "payment")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class PaymentEntity extends TenantOwnedEntity {

    /** Provider code of counter / waiter payments. It is never a registered gateway: no provider API is ever called. */
    public static final String OFFLINE_PROVIDER = "OFFLINE";

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Gateway code, e.g. RAZORPAY or PAYU, or OFFLINE for counter payments. */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_order_id", nullable = false, length = 64)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 20)
    private String method;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "captured_amount_paise")
    private Long capturedAmountPaise;

    @Column(name = "provider_refund_id", length = 64)
    private String providerRefundId;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 20)
    private RefundStatus refundStatus;

    /** Staff user who took an OFFLINE payment. */
    @Column(name = "recorded_by_staff_id")
    private Long recordedByStaffId;

    public boolean isOffline() {
        return OFFLINE_PROVIDER.equalsIgnoreCase(provider);
    }
}
