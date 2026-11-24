package com.heuristiq.paymentservice.wallet.controller;

import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.security.OwnershipGuard;
import com.heuristiq.paymentservice.shared.dto.ResponseObject;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import com.heuristiq.paymentservice.wallet.dto.CreateWalletTopupRequest;
import com.heuristiq.paymentservice.wallet.dto.WalletTopupResponse;
import com.heuristiq.paymentservice.wallet.service.WalletBalanceService;
import com.heuristiq.paymentservice.wallet.service.WalletTopupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The gateway side of wallet top-ups, plus one deprecated balance pass-through. Balances and the transaction list
 * belong to Rider_WalletService; customers read them from the wallet API.
 */
@RestController
@RequestMapping("/api/v1/payment/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletTopupService walletTopupService;
    private final WalletBalanceService walletBalanceService;

    /**
     * Deprecated read-only pass-through for Customer app versions that still call it: asks Rider_WalletService for the
     * balance and answers with the shape those apps parse ({@code object.balance}, a number). Payment keeps no balance.
     * Same access rule as before: the customer themselves, or an authorised admin/service. When the wallet cannot
     * answer the reply is 503, never 0.
     *
     * @deprecated read {@code GET /api/v1/wallet/me} (Rider_WalletService) instead. Remove this method, its client and
     *             its privilege entry once no installed Customer app version calls it (check the gateway access counts
     *             for this path; the apps must first ship the {@code /v1/wallet/me} reader).
     */
    @Deprecated(since = "2026-11")
    @Operation(summary = "Wallet balance (DEPRECATED: use GET /api/v1/wallet/me)",
            description = "Pass-through to Rider_WalletService, kept for installed Customer apps. Removal condition: no "
                    + "installed app version calls it any more. 503 when the wallet is unavailable.",
            deprecated = true)
    @GetMapping("/{customerId}/balance")
    public ResponseEntity<ResponseObject> getBalance(@PathVariable String customerId) {
        OwnershipGuard.requireSelfOrPrivileged(customerId);
        Money balance = walletBalanceService.balanceOf(customerId);
        return ResponseEntity.ok().header("Deprecation", "true")
                .body(ResponseObject.builder().status(true).object(Map.of("balance", balance)).build());
    }

    /**
     * Starts a real, gateway-verified wallet top-up (checkout session creation) - replaces the
     * old {@code POST /{customerId}/topup}, which credited the wallet directly off the caller's
     * own amount with no payment verification at all. See WalletTopupService's javadoc.
     */
    @PostMapping("/topup")
    public ResponseEntity<ResponseObject> createTopup(@Valid @RequestBody CreateWalletTopupRequest req) {
        OwnershipGuard.requireSelfOrPrivileged(req.customerId());
        WalletTopupResponse response = walletTopupService.createTopupSession(req);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /**
     * The top-up's status. While the gateway has confirmed payment but the wallet has not yet booked the credit the
     * status is PAID_AWAITING_CREDIT (the customer app keeps polling); it becomes CAPTURED once the wallet holds it.
     */
    @GetMapping("/topup/{topupId}")
    public ResponseEntity<ResponseObject> getTopup(@PathVariable String topupId) {
        WalletTopupResponse response = walletTopupService.getTopup(topupId);
        if (!OwnershipGuard.isPartyOrPrivileged(response.customerId())) {
            throw new NotFoundException("Wallet top-up not found: " + topupId);
        }
        return ResponseEntity.ok(ResponseObject.builder().status(true).object(response).build());
    }

    /**
     * Razorpay webhook for wallet top-ups. Consumes the raw request body (not a parsed DTO)
     * because signature verification is computed over the exact bytes Razorpay signed - same
     * contract as PaymentController's ride-payment webhook.
     */
    @PostMapping("/topup/webhook")
    public ResponseEntity<ResponseObject> handleTopupWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = PaymentCommonConstants.RAZORPAY_SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = PaymentCommonConstants.RAZORPAY_EVENT_ID_HEADER, required = false) String eventId) {
        walletTopupService.handleTopupWebhook(rawBody, signature, eventId);
        return ResponseEntity.ok(ResponseObject.builder().status(true).object("ok").build());
    }
}
