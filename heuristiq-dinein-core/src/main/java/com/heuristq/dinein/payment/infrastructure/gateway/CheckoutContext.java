package com.heuristq.dinein.payment.infrastructure.gateway;

/**
 * Everything a gateway may need to create an order or a checkout payload.
 *
 * @param providerOrderId null when creating the provider order
 * @param callbackUrl     backend URL a redirect-based provider posts the result to
 * @param returnUrl       frontend page to land on after payment
 */
public record CheckoutContext(Long orderId, String orderNumber, long amountPaise, String currency,
                              String customerName, String customerPhone, String restaurantName, String brandColor,
                              String providerOrderId, String callbackUrl, String returnUrl) {

    public CheckoutContext withProviderOrderId(String id) {
        return new CheckoutContext(orderId, orderNumber, amountPaise, currency, customerName, customerPhone,
                restaurantName, brandColor, id, callbackUrl, returnUrl);
    }
}
