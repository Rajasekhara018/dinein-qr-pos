package com.heuristiq.payment.payment.gateway.payu;

import java.math.BigDecimal;
import com.heuristiq.payment.payment.gateway.util.GatewayAmountParser;
import com.heuristiq.payment.shared.PaymentCommonConstants;
import com.heuristiq.payment.payment.gateway.GatewayClientConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.config.PayuGatewayProperties;
import com.heuristiq.payment.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.payment.payment.gateway.util.Sha512Hasher;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * PayU "verify_payment" postservice.php command — ported from the reference design's
 * PayuStatusClient. Uses payment.providerOrderId as PayU's txnid (the value
 * PayuCreateSessionGatewayClient set at checkout time).
 */
@Slf4j
@Component
public class PayuStatusGatewayClient implements StatusCheckGatewayClient {

    private final WebClient webClient;
    private final PayuGatewayProperties props;
    private final ObjectMapper objectMapper;

    public PayuStatusGatewayClient(WebClient paymentGatewayWebClient, PayuGatewayProperties props, ObjectMapper objectMapper) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.PAYU;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayStatusResult fetchStatus(Payment payment) {
        if (!StringUtils.hasText(props.getKey()) || !StringUtils.hasText(saltV1())) {
            throw new PaymentServiceException("PayU integration is not configured yet");
        }
        String txnId = payment.getProviderOrderId();
        if (!StringUtils.hasText(txnId)) {
            throw new PaymentServiceException("Cannot check PayU status: payment has no providerOrderId (txnid) on record");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", props.getKey());
        form.add("command", PayuConstants.VERIFY_COMMAND);
        form.add("var1", txnId);
        form.add("hash", Sha512Hasher.hex(props.getKey() + "|" + PayuConstants.VERIFY_COMMAND + "|" + txnId + "|" + saltV1()));

        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri(props.getPostServiceUrl())
                    .headers(this::formHeaders)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("PayU verify_payment rejected for txn {}: {} {}", txnId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("PayU status check failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (WebClientException e) {
            log.error("PayU verify_payment unreachable for txn {}: {}", txnId, e.getMessage());
            throw new PaymentServiceException("PayU status check unavailable. Please try again.");
        }

        Map<String, Object> response = parseJson(responseBody);
        Map<String, Object> details = extractDetails(response, txnId);
        if (details == null || details.isEmpty()) {
            throw new PaymentServiceException("PayU status: no transaction details for " + txnId);
        }

        BigDecimal amount = GatewayAmountParser.rupees(firstNonBlank(val(details, "amt"), val(details, "amount")));
        return new GatewayStatusResult(getGatewayType().name(), txnId, val(details, "mihpayid"),
                resolveStatus(details), val(details, "status"), amount, firstNonBlank(val(details, "currency"), payment.getCurrency()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDetails(Map<String, Object> response, String txnId) {
        Object details = response.get("transaction_details");
        if (details instanceof Map<?, ?> dm) {
            Object byTxn = dm.get(txnId);
            if (byTxn instanceof Map<?, ?>) {
                return (Map<String, Object>) byTxn;
            }
            if (dm.containsKey("mihpayid") || dm.containsKey("status")) {
                return (Map<String, Object>) dm;
            }
        }
        return response;
    }

    private String resolveStatus(Map<String, Object> data) {
        String s = val(data, "status");
        String u = val(data, "unmappedstatus");
        // PayU also reports status=success for authorization; funds are not captured yet.
        if ("auth".equalsIgnoreCase(u)) {
            return GatewayClientConstants.RESULT_PENDING;
        }
        if ("success".equalsIgnoreCase(s) || "captured".equalsIgnoreCase(u)) {
            return GatewayClientConstants.RESULT_SUCCESS;
        }
        if ("pending".equalsIgnoreCase(s) || "in progress".equalsIgnoreCase(s)) {
            return GatewayClientConstants.RESULT_PENDING;
        }
        return GatewayClientConstants.RESULT_FAILED;
    }

    private String saltV1() {
        return StringUtils.hasText(props.getSaltV1()) ? props.getSaltV1() : props.getSalt();
    }

    private void formHeaders(HttpHeaders h) {
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.ALL));
    }

    private Map<String, Object> parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            throw new PaymentServiceException("PayU returned empty response");
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new PaymentServiceException("Unable to parse PayU response: " + body);
        }
    }

    private String val(Map<String, Object> m, String key) {
        return m == null || m.get(key) == null ? null : String.valueOf(m.get(key));
    }

    private String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : b;
    }
}
