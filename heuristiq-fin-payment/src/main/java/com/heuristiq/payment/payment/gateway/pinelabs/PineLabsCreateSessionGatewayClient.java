package com.heuristiq.payment.payment.gateway.pinelabs;

import com.heuristiq.payment.shared.PaymentCommonConstants;
import com.heuristiq.payment.payment.gateway.GatewayClientConstants;
import com.heuristiq.phone.PhoneNumbers;
import com.heuristiq.payment.config.PineLabsGatewayProperties;
import com.heuristiq.payment.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pine Labs checkout: acquire an access token (PineLabsAuthGatewayClient), then POST an order —
 * response carries a hosted redirect_url the customer's browser is sent to. Ported from the
 * reference design's PineLabsCheckoutClient, including its snake_case-with-camelCase-fallback
 * response parsing.
 */
@Slf4j
@Component
public class PineLabsCreateSessionGatewayClient implements CreateSessionGatewayClient {


    private final WebClient webClient;
    private final PineLabsGatewayProperties props;
    private final PineLabsAuthGatewayClient authClient;

    public PineLabsCreateSessionGatewayClient(WebClient paymentGatewayWebClient, PineLabsGatewayProperties props,
                                               PineLabsAuthGatewayClient authClient) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
        this.authClient = authClient;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.PINELABS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewaySessionResult createSession(Payment payment) {
        authClient.assertConfigured();
        if (!StringUtils.hasText(props.getCallbackUrl())) {
            throw new PaymentServiceException("Pine Labs callback URL is not configured");
        }

        String accessToken = authClient.generateAccessToken();

        Map<String, Object> body;
        try {
            body = webClient.post()
                    .uri(props.getBaseUrl() + PineLabsConstants.ORDERS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .bodyValue(buildOrderRequest(payment))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Unable to create Pine Labs order [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("Unable to create Pine Labs order (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (WebClientException e) {
            log.error("Pine Labs order creation unreachable: {}", e.getMessage());
            throw new PaymentServiceException("Pine Labs gateway unavailable. Please try again.");
        }

        Object orderIdValue = firstNonNull(body, "order_id", "orderId");
        Object redirectUrlValue = firstNonNull(body, "redirect_url", "redirectUrl");
        if (body == null || orderIdValue == null || redirectUrlValue == null) {
            throw new PaymentServiceException("Invalid response received from Pine Labs");
        }

        String pineLabsOrderId = String.valueOf(orderIdValue);
        return GatewaySessionResult.ofGeneric(pineLabsOrderId, Map.of(
                "redirectUrl", String.valueOf(redirectUrlValue),
                "callbackUrl", props.getCallbackUrl() + payment.getInternalPaymentId()));
    }

    private Object firstNonNull(Map<String, Object> body, String key, String fallbackKey) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value != null ? value : body.get(fallbackKey);
    }

    private Map<String, Object> buildOrderRequest(Payment payment) {
        Map<String, Object> orderAmount = new LinkedHashMap<>();
        orderAmount.put("value", toSubunitAmount(payment.getAmount()));
        orderAmount.put("currency", StringUtils.hasText(payment.getCurrency()) ? payment.getCurrency() : PaymentCommonConstants.DEFAULT_CURRENCY);

        Map<String, Object> customer = customerDetails(payment);

        Map<String, Object> purchaseDetails = new LinkedHashMap<>();
        purchaseDetails.put("customer", customer);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("merchant_order_reference", payment.getInternalPaymentId());
        order.put("order_amount", orderAmount);
        // REDIRECT mode (Pine Labs' own default): the whole browser tab goes to the hosted
        // redirect_url; IFRAME mode would need a postMessage listener this service has no
        // frontend counterpart for -- do not switch without adding one.
        order.put("integration_mode", "REDIRECT");
        order.put("callback_url", props.getCallbackUrl() + payment.getInternalPaymentId());
        order.put("pre_auth", false);
        order.put("allowed_payment_methods", List.of("CARD", "UPI", "NETBANKING", "WALLET"));
        order.put("notes", "Payment for ride " + payment.getRideId());
        order.put("purchase_details", purchaseDetails);
        return order;
    }

    /**
     * The order's {@code customer} object. The calling code and national number come from the customer's own phone number
     * (any country); when the payment carries no valid number neither is sent, rather than guessing a country.
     */
    static Map<String, Object> customerDetails(Payment payment) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customer_id", payment.getCustomerId());
        PhoneNumbers.parse(payment.getCustomerMobile()).ifPresent(phone -> {
            customer.put("country_code", phone.countryCode());
            customer.put("mobile_number", phone.nationalNumber());
        });
        return customer;
    }

    private long toSubunitAmount(java.math.BigDecimal amount) {
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            throw new PaymentServiceException("invalid amount for Pine Labs");
        }
    }
}
