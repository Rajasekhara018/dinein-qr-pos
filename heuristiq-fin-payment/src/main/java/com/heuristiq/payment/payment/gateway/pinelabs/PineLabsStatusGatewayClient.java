package com.heuristiq.paymentservice.payment.gateway.pinelabs;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.payment.gateway.util.GatewayAmountParser;
import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.gateway.GatewayClientConstants;
import com.heuristiq.paymentservice.config.PineLabsGatewayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Pine Labs order-status poll (also needs a fresh access token) — ported from the reference
 * design's PineLabsStatusClient.
 */
@Slf4j
@Component
public class PineLabsStatusGatewayClient implements StatusCheckGatewayClient {

    private final WebClient webClient;
    private final PineLabsGatewayProperties props;
    private final PineLabsAuthGatewayClient authClient;

    public PineLabsStatusGatewayClient(WebClient paymentGatewayWebClient, PineLabsGatewayProperties props,
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
    public GatewayStatusResult fetchStatus(Payment payment) {
        authClient.assertConfigured();
        String orderId = payment.getProviderOrderId();
        if (!StringUtils.hasText(orderId)) {
            throw new PaymentServiceException("Cannot check Pine Labs status: payment has no providerOrderId on record");
        }

        String accessToken = authClient.generateAccessToken();

        Map<String, Object> body;
        try {
            body = webClient.get()
                    .uri(props.getBaseUrl() + PineLabsConstants.ORDER_PATH + orderId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Unable to fetch Pine Labs order status [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("Unable to fetch Pine Labs order status (" + e.getStatusCode() + ")");
        } catch (WebClientException e) {
            log.error("Pine Labs status endpoint unreachable: {}", e.getMessage());
            throw new PaymentServiceException("Pine Labs gateway unavailable. Please try again.");
        }

        Map<String, Object> data = body != null ? (Map<String, Object>) body.get("data") : null;
        if (data == null) {
            throw new PaymentServiceException("Unable to get Pine Labs payment status for " + orderId);
        }

        String rawStatus = str(data.get("status"));
        String status = resolveStatus(rawStatus);
        BigDecimal amount = data.get("amount") != null ? GatewayAmountParser.fromSubunits(str(data.get("amount"))) : null;
        return new GatewayStatusResult(getGatewayType().name(), orderId, null, status, rawStatus, amount,
                str(data.getOrDefault("currency", payment.getCurrency())));
    }

    private String resolveStatus(String pineStatus) {
        if ("PROCESSED".equalsIgnoreCase(pineStatus)) {
            return GatewayClientConstants.RESULT_SUCCESS;
        }
        if ("FAILED".equalsIgnoreCase(pineStatus) || "CANCELLED".equalsIgnoreCase(pineStatus)) {
            return GatewayClientConstants.RESULT_FAILED;
        }
        return GatewayClientConstants.RESULT_PENDING;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
