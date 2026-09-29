package com.heuristq.dinein.payment.dto;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.payment.infrastructure.gateway.CheckoutMode;

import java.util.Map;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * What the browser needs to start paying. {@code mode} tells the client how to use {@code checkout}:
     * SDK (e.g. Razorpay Checkout.js options), FORM_POST ({@code action} + {@code fields}) or REDIRECT ({@code url}).
     * When the order is no longer payable (e.g. an idempotent replay after payment) provider fields are null and
     * {@code status} tells the client where to go.
     */
    public record CheckoutResponse(Long orderId, String orderNumber, int displayToken, OrderStatus status,
                                   String provider, CheckoutMode mode, Map<String, Object> checkout,
                                   Long amountPaise, String currency, String restaurantName) {
    }
}
