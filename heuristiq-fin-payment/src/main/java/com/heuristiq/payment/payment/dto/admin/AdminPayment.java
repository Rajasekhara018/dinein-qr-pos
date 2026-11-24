package com.heuristiq.paymentservice.payment.dto.admin;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.payment.model.Payment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;

/**
 * Raw (unwrapped) payment view for the service-authenticated admin API
 * (/api/v1/internal/admin/payments). Timestamps are Instants, serialized as UTC ISO-8601.
 * The entity stores {@link LocalDateTime} written with {@code LocalDateTime.now()}, i.e. in the
 * JVM default zone, so conversion uses that same zone (a no-op when the JVM runs in UTC).
 */
public record AdminPayment(
        String paymentId,
        String providerPaymentId,
        String providerOrderId,
        String rideId,
        String customerId,
        String customerName,
        String customerEmail,
        String customerMobile,
        String driverId,
        Money amount,
        String status,
        String method,
        String gateway,
        String failureReason,
        String invoiceNumber,
        String receiptNumber,
        Instant createdAt,
        Instant updatedAt,
        Instant settledAt
) {
    public static AdminPayment from(Payment p) {
        return new AdminPayment(
                p.getInternalPaymentId(),
                p.getProviderPaymentId(),
                p.getProviderOrderId(),
                p.getRideId(),
                p.getCustomerId(),
                p.getCustomerName(),
                p.getCustomerEmail(),
                p.getCustomerMobile(),
                p.getDriverId(),
                Money.ofNullable(p.getAmount(), p.getCurrency()),
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getMethod() != null ? p.getMethod().name() : null,
                p.getGateway() != null ? p.getGateway().name() : null,
                p.getFailureReason(),
                p.getInvoiceNumber(),
                p.getReceiptNumber(),
                toInstant(p.getCreatedAt()),
                toInstant(p.getUpdatedAt()),
                toInstant(p.getSettledAt())
        );
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    public static LocalDateTime toLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }
}
