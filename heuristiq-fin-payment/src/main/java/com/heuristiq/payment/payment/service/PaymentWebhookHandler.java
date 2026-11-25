package com.heuristiq.payment.payment.service;

import com.heuristiq.payment.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.payment.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.payment.gatewayaudit.WebhookObservation;
import com.heuristiq.payment.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.payment.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentStateMachine;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.model.PaymentWebhookEvent;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.payment.repository.PaymentWebhookEventRepository;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Razorpay's ride-payment webhook receiver — extracted out of PaymentService so payment
 * creation/retrieval and webhook processing aren't one class. Mirrors WalletTopupService's own
 * (separate) wallet-topup webhook handling, sharing the same RazorpayWebhookSignatureVerifier.
 *
 * MUST be idempotent (HLD §18): the same event delivered twice (Razorpay retries on non-2xx, and
 * duplicate delivery is possible even on success) must only apply the state transition and its
 * side effects (earnings computation, Ride-service notification) once.
 *
 * Dedupe key: the provider's own event id when present (X-Razorpay-Event-Id, populated by our
 * webhook config / synthesized upstream), else a SHA-256 digest of the raw payload — either way,
 * retried/duplicate deliveries of byte-identical payloads collide on the same key and are
 * short-circuited before any state change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final RazorpayWebhookSignatureVerifier webhookSignatureVerifier;
    private final PaymentTransitionEffects transitionEffects;
    private final GatewayAmountVerifier amountVerifier;
    private final WebhookAuditRecorder webhookAudit;

    @Transactional
    public void handleWebhook(String rawBody, String signature, String providerEventIdHeader) {
        String eventId = (providerEventIdHeader != null && !providerEventIdHeader.isBlank())
                ? providerEventIdHeader
                : webhookSignatureVerifier.sha256Hex(rawBody);

        if (!webhookSignatureVerifier.verify(rawBody, signature)) {
            // Recorded in its own transaction (and without the payload or signature) so it survives the throw below.
            webhookAudit.recordUndelivered(new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY, null, eventId, false, WebhookOutcome.REJECTED, null));
            throw new PaymentServiceException("Webhook signature verification failed");
        }

        Optional<PaymentWebhookEvent> alreadyProcessed = webhookEventRepository.findByProviderEventId(eventId);
        if (alreadyProcessed.isPresent()) {
            log.info("Webhook event {} already processed — ignoring duplicate delivery", eventId);
            PaymentWebhookEvent first = alreadyProcessed.get();
            webhookAudit.recordUndelivered(new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY, first.getEventType(), eventId, true,
                    WebhookOutcome.DUPLICATE, first.getPaymentId()));
            return;
        }

        JSONObject payload = new JSONObject(rawBody);
        String eventType = payload.optString("event", "unknown");
        JSONObject paymentEntity = webhookSignatureVerifier.extractPaymentEntity(payload);
        if (paymentEntity == null) {
            log.warn("Webhook event {} of type {} carried no payment entity — recording and ignoring", eventId, eventType);
            recordWebhookEvent(eventId, null, eventType, WebhookOutcome.IGNORED);
            return;
        }

        String providerOrderId = paymentEntity.optString("order_id", null);
        String providerPaymentId = paymentEntity.optString("id", null);

        Optional<Payment> paymentOpt = (providerOrderId != null)
                ? paymentRepository.findByProviderOrderId(providerOrderId)
                : Optional.empty();
        if (paymentOpt.isEmpty() && providerPaymentId != null) {
            paymentOpt = paymentRepository.findByProviderPaymentId(providerPaymentId);
        }

        if (paymentOpt.isEmpty()) {
            log.warn("Webhook event {} referenced unknown order/payment (order={}, payment={}) — recording and ignoring",
                    eventId, providerOrderId, providerPaymentId);
            recordWebhookEvent(eventId, null, eventType, WebhookOutcome.IGNORED);
            return;
        }

        Payment payment = paymentOpt.get();

        // A payment id different from the one already on record is a different attempt (or a duplicate
        // payment on the same order); never overwrite it, ignore the event and leave it for review.
        String recordedPaymentId = payment.getProviderPaymentId();
        if (providerPaymentId != null && recordedPaymentId != null && !recordedPaymentId.isBlank()
                && !recordedPaymentId.equals(providerPaymentId)) {
            log.error("Webhook event {} ({}) for payment {} carries provider payment id {} but {} is on record — ignoring the event",
                    eventId, eventType, payment.getInternalPaymentId(), providerPaymentId, recordedPaymentId);
            recordWebhookEvent(eventId, payment.getInternalPaymentId(), eventType, WebhookOutcome.IGNORED);
            return;
        }

        if ("payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
            applyCapture(payment, providerPaymentId, paymentEntity);
        } else if ("payment.failed".equals(eventType)) {
            // A failed attempt never writes provider_payment_id and can never move a CAPTURED payment.
            transitionEffects.onPaymentFailed(payment, paymentEntity.optString("error_description", "Payment failed"));
        } else {
            log.info("Webhook event {} of type {} does not drive a state transition — recorded only", eventId, eventType);
        }

        recordWebhookEvent(eventId, payment.getInternalPaymentId(), eventType, WebhookOutcome.PROCESSED);
    }

    /** Captures only when the move is legal and the amount and currency the gateway reports match the payment. */
    private void applyCapture(Payment payment, String providerPaymentId, JSONObject paymentEntity) {
        if (!PaymentStateMachine.canTransition(payment.getStatus(), PaymentStatus.CAPTURED)) {
            log.info("Capture event for payment {} ignored: payment is already {}", payment.getInternalPaymentId(), payment.getStatus());
            return;
        }
        Long reportedPaise = reportedPaise(paymentEntity);
        String reportedCurrency = paymentEntity.optString("currency", null);
        if (!amountVerifier.matches(payment, reportedPaise, reportedCurrency)) {
            transitionEffects.onAmountMismatch(payment, reportedPaise, reportedCurrency);
            return;
        }
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            payment.setProviderPaymentId(providerPaymentId);
        }
        PaymentStateMachine.apply(payment, PaymentStatus.CAPTURED);
        paymentRepository.save(payment);
        transitionEffects.onPaymentCaptured(payment);
    }

    /** The paid amount in paise, or null when absent or not a whole number of paise. */
    private static Long reportedPaise(JSONObject paymentEntity) {
        Object raw = paymentEntity.opt("amount");
        if (!(raw instanceof Number number)) {
            return null;
        }
        BigDecimal value = new BigDecimal(number.toString());
        return value.stripTrailingZeros().scale() > 0 ? null : value.longValue();
    }

    private void recordWebhookEvent(String eventId, String paymentId, String eventType, WebhookOutcome outcome) {
        String rowId = UUID.randomUUID().toString();
        webhookEventRepository.save(PaymentWebhookEvent.builder()
                .id(rowId)
                .providerEventId(eventId)
                .claimedEventId(eventId)
                .provider(GatewayAuditConstants.PROVIDER_RAZORPAY)
                .signatureValid(true)
                .outcome(outcome.name())
                .paymentId(paymentId)
                .eventType(eventType)
                .processedAt(LocalDateTime.now())
                .build());
        String tenantId = paymentId == null ? null : paymentRepository.findById(paymentId).map(Payment::getTenantId).orElse(null);
        webhookAudit.publishProcessed(rowId, new WebhookObservation(GatewayAuditConstants.PROVIDER_RAZORPAY, eventType, eventId, true, outcome, paymentId, tenantId));
    }
}
