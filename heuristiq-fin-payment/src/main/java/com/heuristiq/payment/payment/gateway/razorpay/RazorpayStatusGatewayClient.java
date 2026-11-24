package com.heuristiq.payment.payment.gateway.razorpay;

import com.heuristiq.payment.payment.gateway.util.GatewayAmountParser;
import com.heuristiq.payment.shared.PaymentCommonConstants;
import com.heuristiq.payment.payment.gateway.GatewayClientConstants;
import com.heuristiq.payment.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Razorpay order/payment status poll — GET /orders/{id} on the already-authenticated
 * {@code razorpayWebClient} (base URL + Basic auth preset in RestClientConfig). Ported from the
 * reference design's RazorpayStatusClient, trimmed to what this service's Payment model needs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayStatusGatewayClient implements StatusCheckGatewayClient {


    private final WebClient razorpayWebClient;

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayStatusResult fetchStatus(Payment payment) {
        String orderId = payment.getProviderOrderId();
        if (orderId == null || orderId.isBlank()) {
            throw new PaymentServiceException("Cannot check Razorpay status: payment has no providerOrderId on record");
        }
        try {
            Map<String, Object> order = razorpayWebClient.get()
                    .uri("/orders/{id}", orderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
            if (order == null) {
                throw new PaymentServiceException("Razorpay order status lookup returned no body");
            }
            String status = resolveStatus(str(order.get("status")));
            BigDecimal amount = order.get("amount") != null
                    ? GatewayAmountParser.fromSubunits(str(order.get("amount")))
                    : null;
            String providerPaymentId = payment.getProviderPaymentId();
            if (GatewayClientConstants.RESULT_SUCCESS.equals(status) && (providerPaymentId == null || providerPaymentId.isBlank())) {
                providerPaymentId = fetchCapturedPaymentId(orderId);
            }
            return new GatewayStatusResult(getGatewayType().name(), orderId, providerPaymentId,
                    status, str(order.get("status")), amount, str(order.getOrDefault("currency", payment.getCurrency())));
        } catch (WebClientException e) {
            log.error("Razorpay status check failed for order {}: {}", orderId, e.getMessage());
            throw new PaymentServiceException("Razorpay status check unavailable. Please try again.");
        }
    }

    /** The order itself carries no payment id; it is on the order's captured payment. Best effort. */
    @SuppressWarnings("unchecked")
    private String fetchCapturedPaymentId(String orderId) {
        try {
            Map<String, Object> body = razorpayWebClient.get()
                    .uri("/orders/{id}/payments", orderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
            Object items = body == null ? null : body.get("items");
            if (items instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> p && "captured".equalsIgnoreCase(str(p.get("status")))) {
                        return str(p.get("id"));
                    }
                }
            }
        } catch (WebClientException e) {
            log.warn("Could not look up the captured payment id for Razorpay order {}: {}", orderId, e.getMessage());
        }
        return null;
    }

    private String resolveStatus(String razorpayStatus) {
        if ("paid".equalsIgnoreCase(razorpayStatus)) {
            return GatewayClientConstants.RESULT_SUCCESS;
        }
        if ("attempted".equalsIgnoreCase(razorpayStatus) || "created".equalsIgnoreCase(razorpayStatus)) {
            return GatewayClientConstants.RESULT_PENDING;
        }
        return GatewayClientConstants.RESULT_FAILED;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
