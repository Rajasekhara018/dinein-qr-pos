package com.heuristiq.payment.payment.gateway.payu;

import com.heuristiq.payment.payment.gateway.GatewayClientConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.config.PayuGatewayProperties;
import com.heuristiq.payment.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.payment.payment.gateway.util.Sha512Hasher;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.shared.exception.RefundOutcomeUnknownException;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * PayU refund: unlike Razorpay's simple REST refund endpoint, PayU refunds go through the
 * postservice.php "cancel_refund_transaction" command (its own request/auth shape, NOT the
 * hosted-checkout hash from PayuCreateSessionGatewayClient) — ported from the reference design's
 * PayuRefundClient. Auth here is a separate hash: sha512(key|command|mihpayid|salt).
 *
 * mihpayid (PayU's own payment id) is expected to be stored in Payment.providerPaymentId — same
 * slot Razorpay populates with its own payment id.
 */
@Slf4j
@Component
public class PayuRefundGatewayClient implements RefundGatewayClient {


    private final WebClient webClient;
    private final PayuGatewayProperties props;
    private final ObjectMapper objectMapper;

    public PayuRefundGatewayClient(WebClient paymentGatewayWebClient, PayuGatewayProperties props, ObjectMapper objectMapper) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.PAYU;
    }

    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason) {
        return refund(payment, amount, reason, null);
    }

    /** PayU's {@code var2} is a merchant-generated unique refund token, so it carries the refund reference. */
    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason, String reference) {
        if (!StringUtils.hasText(props.getKey()) || !StringUtils.hasText(saltV1())) {
            throw new PaymentServiceException("PayU integration is not configured yet");
        }
        String mihpayid = payment.getProviderPaymentId();
        if (!StringUtils.hasText(mihpayid)) {
            throw new PaymentServiceException("Cannot refund via PayU: payment has no providerPaymentId (mihpayid) on record");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", props.getKey());
        form.add("command", PayuConstants.REFUND_COMMAND);
        form.add("var1", mihpayid);
        if (StringUtils.hasText(reference)) {
            form.add("var2", reference);
        }
        form.add("var3", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        form.add("hash", Sha512Hasher.hex(props.getKey() + "|" + PayuConstants.REFUND_COMMAND + "|" + mihpayid + "|" + saltV1()));

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
            log.error("PayU refund rejected for payment {}: {} {}", payment.getInternalPaymentId(), e.getStatusCode(), e.getResponseBodyAsString());
            String message = "PayU refund failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString();
            if (e.getStatusCode().is5xxServerError()) {
                throw new RefundOutcomeUnknownException(message);
            }
            throw new PaymentServiceException(message);
        } catch (WebClientException | IllegalStateException e) {
            log.error("PayU refund API unreachable or timed out for payment {}: {}", payment.getInternalPaymentId(), e.getMessage());
            throw new RefundOutcomeUnknownException("PayU refund gateway unavailable. Please try again.");
        }

        Map<String, Object> body = parseJson(responseBody);
        String status = normalizeStatus(val(body, "status"), val(body, "msg"));
        if ("FAILED".equals(status)) {
            throw new PaymentServiceException("PayU refund failed: " + val(body, "msg"));
        }
        String requestId = val(body, "request_id");
        log.info("PayU refund accepted for payment {}: requestId={} status={}", payment.getInternalPaymentId(), requestId, status);
        return new GatewayRefundResult(requestId, status);
    }

    private String normalizeStatus(String status, String message) {
        if ("1".equals(status) || "success".equalsIgnoreCase(status) || "processed".equalsIgnoreCase(status)) {
            return "PROCESSED";
        }
        String msg = message == null ? "" : message.toLowerCase();
        if ("queued".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status) || msg.contains("queued") || msg.contains("pending")) {
            return "QUEUED";
        }
        return "FAILED";
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
            throw new RefundOutcomeUnknownException("PayU returned empty response");
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RefundOutcomeUnknownException("Unable to parse PayU refund response");
        }
    }

    private String val(Map<String, Object> m, String key) {
        return m == null || m.get(key) == null ? null : String.valueOf(m.get(key));
    }
}
