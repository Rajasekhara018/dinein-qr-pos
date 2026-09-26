package com.heuristq.dinein.payment.gateway;

import java.util.Map;

/**
 * Provider-specific data for the browser. Contains public values only (key id, order id, form fields with a hash);
 * secrets never leave the server.
 */
public record CheckoutPayload(CheckoutMode mode, Map<String, Object> data) {
}
