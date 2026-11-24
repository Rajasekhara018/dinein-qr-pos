package com.heuristiq.paymentservice.payment.gateway.toucan;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.payment.gateway.util.GatewayAmountParser;
import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.gateway.GatewayClientConstants;
import com.heuristiq.paymentservice.config.ToucanGatewayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Toucan checkStatus poll — bearer-token authenticated, ported from the reference design's
 * ToucanStatusClient. Simplified to a raw Map response (the reference's GatewayStatusResponse DTO
 * pulls in fields this service's Payment model has no use for).
 */
@Slf4j
@Component
public class ToucanStatusGatewayClient implements StatusCheckGatewayClient {

    private final WebClient webClient;
    private final ToucanGatewayProperties props;

    public ToucanStatusGatewayClient(WebClient paymentGatewayWebClient, ToucanGatewayProperties props) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.TOUCAN;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayStatusResult fetchStatus(Payment payment) {
        if (!StringUtils.hasText(props.getMerchantToken()) || !StringUtils.hasText(props.getMerchantTerminal())) {
            throw new PaymentServiceException("Toucan integration is not configured yet");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getMerchantToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body;
        try {
            body = webClient.post()
                    .uri(props.getBaseUrl() + ToucanConstants.STATUS_PATH)
                    .headers(h -> h.putAll(headers))
                    .bodyValue(buildStatusRequest(payment))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Toucan status check failed [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("Toucan status check failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (WebClientException e) {
            log.error("Toucan status check unreachable: {}", e.getMessage());
            throw new PaymentServiceException("Toucan status check unavailable. Please try again.");
        }

        if (body == null || !Boolean.TRUE.equals(body.get("success")) || body.get("object") == null) {
            throw new PaymentServiceException("Toucan payment status not found for " + payment.getInternalPaymentId());
        }
        Map<String, Object> object = (Map<String, Object>) body.get("object");
        String actionCode = str(object.get("actionCode"));
        String rawStatus = str(object.get("status"));
        String status = "00".equals(actionCode) || "APPROVED".equalsIgnoreCase(rawStatus) ? GatewayClientConstants.RESULT_SUCCESS : GatewayClientConstants.RESULT_FAILED;
        BigDecimal amount = object.get("transactionAmount") instanceof Map<?, ?> ta
                ? GatewayAmountParser.rupees(str(((Map<String, Object>) ta).get("value")))
                : null;

        return new GatewayStatusResult(getGatewayType().name(), payment.getInternalPaymentId(),
                str(object.get("transactionId")), status, rawStatus, amount, str(object.getOrDefault("currency", payment.getCurrency())));
    }

    private Map<String, Object> buildStatusRequest(Payment payment) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("invoiceNumber", payment.getInternalPaymentId());
        object.put("terminalNumber", props.getMerchantTerminal());
        object.put("merchantNumber", StringUtils.hasText(props.getMerchantNumber()) ? props.getMerchantNumber() : "");
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("messageID", ToucanConstants.STATUS_PATH);
        req.put("requestType", "text");
        req.put("object", object);
        return req;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
