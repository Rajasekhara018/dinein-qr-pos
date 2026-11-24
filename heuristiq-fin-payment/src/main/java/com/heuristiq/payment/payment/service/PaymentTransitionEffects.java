package com.heuristiq.paymentservice.payment.service;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.client.NmsServiceClient;
import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.client.dto.NotificationRequest;
import com.heuristiq.paymentservice.client.dto.PaymentResultNotification;
import com.heuristiq.paymentservice.payment.dto.ReceiptResponse;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentStateMachine;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService;
import com.heuristiq.paymentservice.salesettlement.domain.SaleSettlementConstants;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Everything that happens once a payment's outcome is actually known — CAPTURED (receipt
 * generation, Ride/NMS notification; the driver's earnings are booked in Rider_WalletService with the ride's sale) or FAILED (Ride/NMS
 * notification) — shared by every path that can learn that outcome: cash's synchronous capture
 * (PaymentService), the Razorpay webhook (PaymentWebhookHandler), and the PAYU/TOUCAN/PINELABS
 * poll (PaymentStatusPollingService). Extracted out of PaymentService so those three callers
 * depend on one focused collaborator instead of the whole of PaymentService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTransitionEffects {

    private final PaymentRepository paymentRepository;
    private final RideServiceClient rideServiceClient;
    private final NmsServiceClient nmsServiceClient;
    private final SaleSettlementService saleSettlement;

    /** The payment's exact amount (zero when it has none). */
    private static BigDecimal amountOf(Payment payment) {
        return payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
    }

    /** The amount reported to the Ride service, with the payment's currency. */
    private static Money moneyOf(Payment payment) {
        return Money.of(amountOf(payment), payment.getCurrency());
    }

    public void onPaymentCaptured(Payment payment) {
        generateReceiptIfAbsent(payment);
        if (isGateway(payment)) {
            // the wallet holds the sale's earnings PENDING until the gateway money is here (docs/FRANCHISE_PLAN.md D7)
            saleSettlement.paymentCaptured(payment.getInternalPaymentId(), payment.getTenantId(), payment.getSourceService(),
                    payment.getSourceReference(), payment.getRideId());
        }
        if (payment.getSourceService() != null) {
            return;   // another application's payment: no ride to tell, and its owner reads the status itself
        }
        rideServiceClient.notifyPaymentResult(payment.getRideId(), PaymentResultNotification.COMPLETED,
                payment.getInternalPaymentId(), moneyOf(payment));
        notifyPaymentResult(payment, "PAYMENT_SUCCESSFUL");
    }

    /**
     * Shared FAILED-transition side effects — the Razorpay webhook's payment.failed branch and
     * PaymentStatusPollingService's poll result both apply identical behavior (Ride-service
     * notification + NMS PAYMENT_FAILED) instead of duplicating it.
     */
    public boolean onPaymentFailed(Payment payment, String failureReason) {
        if (!PaymentStateMachine.apply(payment, PaymentStatus.FAILED)) {
            return false;
        }
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);
        abandonSale(payment, SaleSettlementConstants.REASON_FAILED);
        if (payment.getSourceService() != null) {
            return true;
        }
        rideServiceClient.notifyPaymentResult(payment.getRideId(), PaymentResultNotification.FAILED,
                payment.getInternalPaymentId(), moneyOf(payment));
        notifyPaymentResult(payment, "PAYMENT_FAILED");
        return true;
    }

    /**
     * The poller gave up on a gateway payment still pending after its maximum age: moves it to EXPIRED and asks the wallet to
     * cancel its sale (the cancellation is skipped if the sale is paid after all).
     *
     * @return true when the payment was expired
     */
    public boolean onPaymentExpired(Payment payment) {
        if (!PaymentStateMachine.apply(payment, PaymentStatus.EXPIRED)) {
            return false;
        }
        paymentRepository.save(payment);
        abandonSale(payment, SaleSettlementConstants.REASON_EXPIRED);
        return true;
    }

    /** A gateway payment whose money will not come: its wallet sale (if one was booked) is to be cancelled. */
    private void abandonSale(Payment payment, String reason) {
        if (isGateway(payment)) {
            saleSettlement.paymentAbandoned(payment.getInternalPaymentId(), payment.getTenantId(), payment.getSourceService(),
                    payment.getSourceReference(), payment.getRideId(), reason);
        }
    }

    /**
     * A direct collection (cash or UPI paid to the driver) settles when the driver's cash reaches the company (inside the wallet),
     * never from here.
     */
    private static boolean isGateway(Payment payment) {
        return payment.getMethod() == null || !payment.getMethod().isDirectCollection();
    }

    /**
     * A direct collection recorded by the ride service once the driver confirmed it: only the receipt. The ride is already paid (the
     * ride service told itself), the rider was already notified by the ride service, and the driver's earnings are booked by the
     * ledger's sale journal, so computing them here would count them twice (docs/DRIVER_FRANCHISE_SUBSCRIPTION_PLAN.md, D13).
     */
    public void onDirectCollectionRecorded(Payment payment) {
        generateReceiptIfAbsent(payment);
    }

    /**
     * The gateway reported a paid amount or currency that does not match the payment: the payment is NOT
     * captured. It moves to AMOUNT_MISMATCH (terminal, needs manual review) with the two amounts recorded;
     * no receipt, earnings or customer notification happens.
     *
     * @return true when the payment was flagged
     */
    public boolean onAmountMismatch(Payment payment, Long reportedPaise, String reportedCurrency) {
        if (!PaymentStateMachine.apply(payment, PaymentStatus.AMOUNT_MISMATCH)) {
            return false;
        }
        payment.setFailureReason("AMOUNT_MISMATCH: expectedPaise="
                + (payment.getAmount() == null ? null : GatewayAmountVerifier.toPaise(payment.getAmount()))
                + " reportedPaise=" + reportedPaise + " reportedCurrency=" + reportedCurrency);
        paymentRepository.save(payment);
        log.error("Payment {} flagged AMOUNT_MISMATCH and left uncaptured; manual review required", payment.getInternalPaymentId());
        return true;
    }

    /**
     * Fires a rider-facing NMS notification for the HLD "Payment Result" event (success or
     * failure).
     *
     * Recipient identification: this service only has {@code customerId} on the Payment
     * record (no phone number/email), so notifications are addressed via {@code userIds} — NMS
     * falls back to an internal device-token lookup by user id for FCM_PUSH, and IN_APP is
     * keyed by user id directly. SMS/EMAIL channels would need a phone/email lookup this
     * service doesn't perform — a known gap, not something faked here.
     *
     * Non-fatal by design: NmsServiceClient.notify() already swallows its own failures; this
     * wrapper additionally guards so a notification never risks the payment/webhook transaction
     * that just committed (same "Payment record is already durably persisted" rationale as
     * rideServiceClient.notifyPaymentResult's own best-effort javadoc).
     */
    private void notifyPaymentResult(Payment payment, String event) {
        if (payment.getCustomerId() == null) {
            log.warn("Payment {} has no customerId on record — skipping NMS payment-result notification",
                    payment.getInternalPaymentId());
            return;
        }
        try {
            nmsServiceClient.notify(NotificationRequest.builder()
                    .event(event)
                    .variables(java.util.Map.of(
                            "rideId", String.valueOf(payment.getRideId()),
                            "amount", String.valueOf(payment.getAmount())))
                    .userIds(java.util.List.of(payment.getCustomerId()))
                    .build());
        } catch (Exception e) {
            log.warn("Unexpected failure preparing NMS payment-result notification for payment {}: {}",
                    payment.getInternalPaymentId(), e.getMessage());
        }
    }

    /**
     * Generates and stamps the real receipt number/timestamp (HLD step 22 — "Receipt generated")
     * the first time a payment reaches CAPTURED, from every onPaymentCaptured call site. Idempotent:
     * a payment that already has a receipt (e.g. a duplicate webhook delivery that reaches
     * onPaymentCaptured a second time — shouldn't happen given handleWebhook's own dedupe, but kept
     * safe regardless) is left untouched rather than getting a second receipt number.
     */
    private void generateReceiptIfAbsent(Payment payment) {
        if (payment.getReceiptNumber() != null) {
            return;
        }
        String receiptNumber = "RCPT-" + payment.getInternalPaymentId().substring(0, 8).toUpperCase()
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        payment.setReceiptNumber(receiptNumber);
        payment.setReceiptGeneratedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        log.info("Receipt generated for payment {}: receiptNumber={}", payment.getInternalPaymentId(), receiptNumber);
    }

    public ReceiptResponse getReceipt(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        return ReceiptResponse.from(payment);
    }
}
