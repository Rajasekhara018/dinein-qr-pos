package com.heuristiq.payment.payment.dto;

import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStatus;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * The real receipt for a captured/settled/refunded payment (HLD step 22 — "Receipt generated"),
 * returned from {@code GET /payments/{paymentId}/receipt}.
 *
 * What's real: every field here is sourced directly from this service's own {@link Payment}
 * record — the actual total amount charged, currency, method, ride/customer/driver ids, the
 * receipt/invoice numbers, and the actual timestamps the payment state machine recorded.
 *
 * What's deliberately NOT fabricated: an itemized fare breakdown (base fare, distance/time
 * charge, surge multiplier, discount, tax). {@code RideServiceClient.getRide} — the only upstream
 * source this service calls before creating a payment — returns just {@code estimatedFare}/
 * {@code finalFare} as a single total (see RideDetailsResponse), with no component breakdown.
 * Rather than inventing a fake split of that total, this receipt reports the one real number
 * (the amount actually charged) and says so in {@link #breakdownNote()}. Wiring an itemized
 * breakdown would require a new upstream contract (e.g. a fare-breakdown field on the Ride/
 * Pricing service's response) — out of scope for this pass.
 */
public record ReceiptResponse(
        String receiptNumber,
        String paymentId,
        String rideId,
        String customerId,
        String driverId,
        Money amountCharged,
        String paymentMethod,
        String status,
        String invoiceNumber,
        LocalDateTime paymentCreatedAt,
        LocalDateTime capturedOrSettledAt,
        LocalDateTime receiptGeneratedAt,
        String breakdownNote
) {
    private static final String BREAKDOWN_NOTE =
            "Itemized fare breakdown (base fare, distance/time charge, surge, discount, tax) is not "
            + "available to this service: RideServiceClient.getRide only returns a single "
            + "estimatedFare/finalFare total, not a component breakdown. This receipt reflects the "
            + "real total amount charged, payment method, and the actual timestamps recorded by the "
            + "payment state machine.";

    public static ReceiptResponse from(Payment p) {
        return new ReceiptResponse(
                p.getReceiptNumber(),
                p.getInternalPaymentId(),
                p.getRideId(),
                p.getCustomerId(),
                p.getDriverId(),
                Money.ofNullable(p.getAmount(), p.getCurrency()),
                p.getMethod() != null ? p.getMethod().name() : null,
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getInvoiceNumber(),
                p.getCreatedAt(),
                p.getStatus() == PaymentStatus.SETTLED ? p.getSettledAt() : p.getUpdatedAt(),
                p.getReceiptGeneratedAt(),
                BREAKDOWN_NOTE
        );
    }
}
