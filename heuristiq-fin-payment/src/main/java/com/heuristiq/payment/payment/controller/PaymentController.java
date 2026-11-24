package com.heuristiq.paymentservice.payment.controller;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.dto.CreatePaymentRequest;
import com.heuristiq.paymentservice.payment.dto.PaymentResponse;
import com.heuristiq.paymentservice.payment.dto.ReceiptResponse;
import com.heuristiq.paymentservice.payment.dto.RefundRequest;
import com.heuristiq.paymentservice.payment.lifecycle.PaymentLifecycleService;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.service.PaymentRefundService;
import com.heuristiq.paymentservice.payment.service.PaymentStatusPollingService;
import com.heuristiq.paymentservice.payment.service.PaymentService;
import com.heuristiq.paymentservice.payment.service.PaymentTransitionEffects;
import com.heuristiq.paymentservice.payment.service.PaymentWebhookHandler;
import com.heuristiq.paymentservice.security.OwnershipGuard;
import com.heuristiq.paymentservice.security.TenantAccess;
import com.heuristiq.paymentservice.shared.dto.ResponseObject;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentWebhookHandler paymentWebhookHandler;
    private final PaymentTransitionEffects paymentTransitionEffects;
    private final PaymentRefundService paymentRefundService;
    private final PaymentStatusPollingService paymentStatusPollingService;
    private final PaymentLifecycleService paymentLifecycleService;

    @PostMapping
    public ResponseEntity<ResponseObject> createPayment(@Valid @RequestBody CreatePaymentRequest req) {
        PaymentResponse response = paymentService.createPayment(req, OwnershipGuard.currentPrincipal());
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ResponseObject> getPayment(@PathVariable String paymentId) {
        PaymentResponse response = PaymentResponse.from(readablePayment(paymentId));
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /**
     * Razorpay webhook receiver. Consumes the raw request body (not a parsed DTO) because
     * signature verification is computed over the exact bytes Razorpay signed.
     */
    @PostMapping("/webhook")
    public ResponseEntity<ResponseObject> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = PaymentCommonConstants.RAZORPAY_SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = PaymentCommonConstants.RAZORPAY_EVENT_ID_HEADER, required = false) String eventId) {
        paymentWebhookHandler.handleWebhook(rawBody, signature, eventId);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object("ok").build());
    }

    @GetMapping("/{paymentId}/receipt")
    public ResponseEntity<ResponseObject> getReceipt(@PathVariable String paymentId) {
        readablePayment(paymentId);
        ReceiptResponse response = paymentTransitionEffects.getReceipt(paymentId);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /** The payment's lifecycle, oldest step first (the customer's invoice page shows it). */
    @GetMapping("/{paymentId}/transitions")
    public ResponseEntity<ResponseObject> transitions(@PathVariable String paymentId) {
        return ResponseEntity.ok(ResponseObject.builder().status(true)
                .object(paymentLifecycleService.history(readablePayment(paymentId))).build());
    }

    /**
     * Asks the gateway for the payment's live status and applies it. The checkout page calls this
     * after the customer pays, so a payment does not wait on a webhook that may never arrive.
     */
    @PostMapping("/{paymentId}/reconcile")
    public ResponseEntity<ResponseObject> reconcile(@PathVariable String paymentId) {
        Payment payment = paymentService.getPaymentEntity(paymentId);
        if (!TenantAccess.canSee(payment.getTenantId())
                || !OwnershipGuard.isPartyOrPrivileged(payment.getCustomerId(), payment.getDriverId())) {
            throw new AccessDeniedException("Access denied: you may only act on your own payments");
        }
        paymentStatusPollingService.reconcile(paymentId);
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /** Refunds are admin or backend-service only; a normal user can never refund. */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ResponseObject> refund(@PathVariable String paymentId, @Valid @RequestBody RefundRequest req,
                                                 @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OwnershipGuard.requirePrivileged();
        // A payment of another organization answers 404, like a missing one.
        readablePayment(paymentId);
        PaymentResponse response = paymentRefundService.refund(paymentId, req, null, idempotencyKey);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /**
     * The payment, if the caller is its customer or driver (or an admin/service caller). Any other caller
     * gets 404, so the existence of someone else's payment is not revealed.
     */
    private Payment readablePayment(String paymentId) {
        Payment payment = paymentService.getPaymentEntity(paymentId);
        if (!TenantAccess.canSee(payment.getTenantId())
                || !OwnershipGuard.isPartyOrPrivileged(payment.getCustomerId(), payment.getDriverId())) {
            throw new NotFoundException("Payment not found: " + paymentId);
        }
        return payment;
    }
}
