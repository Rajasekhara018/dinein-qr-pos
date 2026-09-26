package com.heuristq.dinein.order;

import com.heuristq.dinein.guest.CurrentGuest;
import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderSummary;
import com.heuristq.dinein.order.dto.OrderDtos.GuestOrderView;
import com.heuristq.dinein.order.dto.OrderDtos.PlaceOrderRequest;
import com.heuristq.dinein.payment.PaymentService;
import com.heuristq.dinein.payment.dto.PaymentDtos.CheckoutResponse;
import com.heuristq.dinein.payment.gateway.razorpay.RazorpayPaymentGateway;
import com.heuristq.dinein.shared.exception.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicOrderController {

    private final OrderPlacementService placementService;
    private final OrderQueryService queryService;
    private final PaymentService paymentService;

    public PublicOrderController(OrderPlacementService placementService, OrderQueryService queryService,
                                 PaymentService paymentService) {
        this.placementService = placementService;
        this.queryService = queryService;
        this.paymentService = paymentService;
    }

    /** Requires an {@code Idempotency-Key} per checkout attempt; repeating it returns the same order. */
    @PostMapping("/orders")
    public CheckoutResponse place(@CurrentGuest GuestSession guest,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @Valid @RequestBody PlaceOrderRequest request) {
        return placementService.place(guest, idempotencyKey, request);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<GuestOrderSummary>> myOrders(@CurrentGuest GuestSession guest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queryService.guestOrders(guest));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<GuestOrderView> order(@CurrentGuest GuestSession guest, @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queryService.guestOrder(id, guest));
    }

    /** Reuses the same order and Razorpay order while it is still PENDING_PAYMENT. */
    @PostMapping("/orders/{id}/retry-payment")
    public CheckoutResponse retryPayment(@CurrentGuest GuestSession guest, @PathVariable Long id) {
        paymentService.assertOwnedBy(id, guest);
        return paymentService.ensureCheckout(id, true);
    }

    /**
     * Client-side verification after an SDK checkout (e.g. Razorpay's success handler posts
     * {@code razorpay_order_id}, {@code razorpay_payment_id}, {@code razorpay_signature}). An optional
     * {@code provider} field selects the gateway; Razorpay fields imply RAZORPAY.
     */
    @PostMapping("/payments/verify")
    public GuestOrderView verify(@CurrentGuest GuestSession guest, @RequestBody Map<String, String> body) {
        if (body == null || body.size() > 30) {
            throw ApiException.badRequest("BAD_REQUEST", "Malformed request");
        }
        String provider = body.containsKey("provider") ? body.get("provider")
                : body.containsKey("razorpay_order_id") ? RazorpayPaymentGateway.CODE : paymentService.activeProvider();
        Long orderId = paymentService.verifyCallback(provider, body, guest);
        return queryService.guestOrder(orderId, guest);
    }
}
