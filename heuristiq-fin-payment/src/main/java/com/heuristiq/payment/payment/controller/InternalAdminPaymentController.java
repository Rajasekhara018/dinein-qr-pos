package com.heuristiq.paymentservice.payment.controller;

import com.heuristiq.paymentservice.gatewayaudit.admin.AdminGatewayCall;
import com.heuristiq.paymentservice.gatewayaudit.admin.AdminGatewayCallService;
import com.heuristiq.paymentservice.payment.dto.admin.AdminPayment;
import com.heuristiq.paymentservice.payment.dto.admin.AdminPaymentPage;
import com.heuristiq.paymentservice.payment.dto.admin.AdminPaymentSummary;
import com.heuristiq.paymentservice.payment.dto.admin.AdminPaymentTransition;
import com.heuristiq.paymentservice.payment.dto.admin.AdminRefundRequest;
import com.heuristiq.paymentservice.payment.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-authenticated (X-Rider-Service-Token, see SecurityConfig) admin view over the payments
 * source of truth, consumed by Rider_AdminAPI. Responses are raw JSON (not ResponseObject-wrapped);
 * errors are {"status", "error", "message"} — see InternalAdminPaymentExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/internal/admin/payments")
@RequiredArgsConstructor
public class InternalAdminPaymentController {

    private final AdminPaymentService adminPaymentService;
    private final AdminGatewayCallService adminGatewayCallService;

    @GetMapping
    public AdminPaymentPage list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String rideId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminPaymentService.list(
                new AdminPaymentService.Filter(customerId, rideId, driverId, status, method, from, to), page, size);
    }

    @GetMapping("/summary")
    public AdminPaymentSummary summary(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return adminPaymentService.summary(from, to);
    }

    @GetMapping("/{paymentId}")
    public AdminPayment get(@PathVariable String paymentId) {
        return adminPaymentService.get(paymentId);
    }

    @GetMapping("/{paymentId}/transitions")
    public List<AdminPaymentTransition> transitions(@PathVariable String paymentId) {
        return adminPaymentService.transitions(paymentId);
    }

    /**
     * Outbound gateway calls made for the payment (create order, refund, status check), oldest first.
     * Optional {@code operation} (e.g. REFUND) and {@code reference} (a refund reference) narrow the list,
     * which is how a single refund's gateway calls are read.
     */
    @GetMapping("/{paymentId}/gateway-calls")
    public List<AdminGatewayCall> gatewayCalls(@PathVariable String paymentId,
                                               @RequestParam(required = false) String operation,
                                               @RequestParam(required = false) String reference) {
        return adminGatewayCallService.forPayment(paymentId, operation, reference);
    }

    @PostMapping("/{paymentId}/reconcile")
    public AdminPayment reconcile(@PathVariable String paymentId) {
        return adminPaymentService.reconcile(paymentId);
    }

    @PostMapping("/{paymentId}/refund")
    public AdminPayment refund(@PathVariable String paymentId, @RequestBody AdminRefundRequest body,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return adminPaymentService.refund(paymentId, body, idempotencyKey);
    }
}
