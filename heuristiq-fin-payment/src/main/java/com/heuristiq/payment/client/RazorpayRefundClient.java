package com.heuristiq.payment.client;

import com.heuristiq.payment.client.dto.RazorpayRefundResult;
import com.heuristiq.payment.config.RazorpayProperties;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.shared.exception.RefundOutcomeUnknownException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real call to Razorpay's Refund API — POST /v1/payments/{razorpayPaymentId}/refund — replacing
 * the previous "wallet-only, deferred" refund behavior in PaymentService.refund.
 *
 * Deliberately a direct REST call via this service's existing WebClient idiom (mirroring
 * RideServiceClient/NmsServiceClient), not the razorpay-java SDK's payments.refund(...): the
 * auth-header/body-shape/endpoint-path contract here is taken from a verified, tested reference
 * implementation (a sibling project's RazorpayTransactionEnrichmentService.createRefund), rather
 * than guessed from the SDK.
 *
 * Unlike RideServiceClient/NmsServiceClient's "best-effort, log-and-swallow" outbound calls, a
 * refund is a write the caller (PaymentService.refund, and transitively the customer waiting on
 * their money back) is depending on — so failures here are NOT swallowed. They propagate as a
 * PaymentServiceException, which PaymentService.refund lets bubble up (its @Transactional method
 * hasn't mutated the Payment row yet at the point this is called, so a failed gateway refund never
 * leaves the payment falsely marked REFUNDED/PARTIALLY_REFUNDED nor credits the wallet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayRefundClient {

    private final WebClient razorpayWebClient;
    private final RazorpayProperties razorpayProperties;

    /**
     * @param razorpayPaymentId the gateway's own payment id (Payment.providerPaymentId), NOT this
     *                          service's internalPaymentId — required by Razorpay's refund endpoint.
     * @param amountPaise       refund amount in paise; null means a full refund of the captured amount.
     * @param reason            optional free-text reason, sent as notes.reason.
     */
    public RazorpayRefundResult refund(String razorpayPaymentId, Long amountPaise, String reason) {
        return refund(razorpayPaymentId, amountPaise, reason, null);
    }

    /**
     * Same as {@link #refund(String, Long, String)} but idempotent on {@code reference}: it is sent as
     * Razorpay's {@code X-Refund-Idempotency} header, so a repeated call returns the first refund instead
     * of creating a second one. The call is bounded by {@code app.razorpay.refund-timeout-seconds} (default {@link RefundClientConstants#TIMEOUT}).
     *
     * @throws RefundOutcomeUnknownException on timeout, connection failure or a gateway 5xx (the refund
     *                                       may have happened)
     * @throws PaymentServiceException       when Razorpay definitively rejected the refund
     */
    @SuppressWarnings("unchecked")
    public RazorpayRefundResult refund(String razorpayPaymentId, Long amountPaise, String reason, String reference) {
        if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
            throw new PaymentServiceException(
                    "Cannot refund via Razorpay: payment has no providerPaymentId on record");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (amountPaise != null) {
            body.put("amount", amountPaise);
        }
        if (reason != null && !reason.isBlank()) {
            body.put("notes", Map.of("reason", reason));
        }

        try {
            Map<String, Object> response = razorpayWebClient.post()
                    .uri("/payments/{id}/refund", razorpayPaymentId)
                    .headers(h -> {
                        if (reference != null && !reference.isBlank()) {
                            h.set(RefundClientConstants.IDEMPOTENCY_HEADER, reference);
                        }
                    })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(razorpayProperties.getRefundTimeoutSeconds()));

            String refundId = response != null && response.get("id") != null ? String.valueOf(response.get("id")) : null;
            String status = response != null && response.get("status") != null ? String.valueOf(response.get("status")) : null;
            log.info("Razorpay refund succeeded for payment {}: refundId={} status={}", razorpayPaymentId, refundId, status);
            return new RazorpayRefundResult(refundId, status);
        } catch (WebClientResponseException e) {
            log.error("Razorpay refund API rejected the request for payment {}: {} {}",
                    razorpayPaymentId, e.getStatusCode(), e.getResponseBodyAsString());
            String message = "Razorpay refund failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString();
            if (e.getStatusCode().is5xxServerError()) {
                throw new RefundOutcomeUnknownException(message);
            }
            throw new PaymentServiceException(message);
        } catch (WebClientException | IllegalStateException e) {
            log.error("Razorpay refund API unreachable or timed out for payment {}: {}", razorpayPaymentId, e.getMessage());
            throw new RefundOutcomeUnknownException("Razorpay refund gateway unavailable. Please try again.");
        }
    }
}
