package com.heuristiq.paymentservice.payment.dto;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.model.Payment;

import java.time.LocalDateTime;
import java.util.Map;
import java.math.BigDecimal;

/**
 * @param gateway        Null for CASH. For an online payment, one of RAZORPAY/PAYU/TOUCAN/PINELABS.
 * @param razorpayOrder  Populated ONLY for gateway=RAZORPAY, right after creation — unchanged
 *                        shape/behavior for existing Ride/Dispatch-service callers.
 * @param gatewaySession Populated for gateway=PAYU/TOUCAN/PINELABS right after creation (never for
 *                        RAZORPAY, which uses razorpayOrder instead, or CASH). Generic
 *                        gateway-specific checkout payload — see each adapter's javadoc for its shape
 *                        (PayU: hosted-form action/fields; Toucan/PineLabs: a redirectUrl).
 */
public record PaymentResponse(
        String paymentId,
        String providerPaymentId,
        String providerOrderId,
        String rideId,
        String customerId,
        Money amount,
        String status,
        String method,
        String gateway,
        String invoiceNumber,
        String receiptNumber,
        String customerName,
        String customerEmail,
        String customerMobile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RazorpayOrderDetails razorpayOrder,
        Map<String, Object> gatewaySession,
        String sourceService,
        String sourceReference
) {
    public static PaymentResponse from(Payment p) {
        return from(p, null);
    }

    public static PaymentResponse from(Payment p, GatewaySessionResult session) {
        return new PaymentResponse(
                p.getInternalPaymentId(),
                p.getProviderPaymentId(),
                p.getProviderOrderId(),
                p.getRideId(),
                p.getCustomerId(),
                Money.ofNullable(p.getAmount(), p.getCurrency()),
                p.getStatus().name(),
                p.getMethod().name(),
                p.getGateway() != null ? p.getGateway().name() : null,
                p.getInvoiceNumber(),
                p.getReceiptNumber(),
                p.getCustomerName(),
                p.getCustomerEmail(),
                p.getCustomerMobile(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                session != null ? session.razorpayOrder() : null,
                session != null ? session.gatewaySession() : null,
                p.getSourceService(),
                p.getSourceReference()
        );
    }
}
