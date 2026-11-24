package com.heuristiq.paymentservice.payment.gateway.toucan;

import com.heuristiq.paymentservice.payment.gateway.GatewayClientConstants;
import com.heuristiq.paymentservice.config.ToucanGatewayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.util.Sha512Hasher;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.Map;

/**
 * Toucan checkout: POST a bearer/merchant-token-authenticated form to getpaymentsession, then
 * follow the redirect Location header it returns — ported from the reference design's
 * ToucanCheckoutClient. Toucan's own terminal/merchant-token are configured server-side
 * (payment.gateway.toucan.merchant*) since this service's Payment model carries no per-request
 * terminal override.
 */
@Slf4j
@Component
public class ToucanCreateSessionGatewayClient implements CreateSessionGatewayClient {


    private final WebClient webClient;
    private final ToucanGatewayProperties props;

    public ToucanCreateSessionGatewayClient(WebClient paymentGatewayWebClient, ToucanGatewayProperties props) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.TOUCAN;
    }

    @Override
    public GatewaySessionResult createSession(Payment payment) {
        if (!StringUtils.hasText(props.getMerchantToken()) || !StringUtils.hasText(props.getMerchantTerminal())) {
            throw new PaymentServiceException("Toucan integration is not configured yet");
        }

        MultiValueMap<String, String> form = buildForm(payment);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        try {
            ResponseEntity<String> response = webClient.post()
                    .uri(props.getBaseUrl() + ToucanConstants.SESSION_PATH)
                    .headers(h -> h.putAll(headers))
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .toEntity(String.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
            String redirectUrl = response != null ? response.getHeaders().getFirst(HttpHeaders.LOCATION) : null;
            return GatewaySessionResult.ofGeneric(payment.getInternalPaymentId(), Map.of("redirectUrl", redirectUrl == null ? "" : redirectUrl));
        } catch (WebClientResponseException e) {
            log.error("Toucan session creation failed [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("Toucan session creation failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (WebClientException e) {
            log.error("Toucan session creation unreachable: {}", e.getMessage());
            throw new PaymentServiceException("Toucan gateway unavailable. Please try again.");
        }
    }

    private MultiValueMap<String, String> buildForm(Payment payment) {
        LinkedMultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("t", props.getMerchantTerminal());
        f.add("o", payment.getInternalPaymentId());
        f.add("ta", String.valueOf(payment.getAmount()));
        f.add("c", StringUtils.hasText(payment.getCurrency()) ? payment.getCurrency() : props.getCurrency());
        f.add("mac", props.getMerchantToken());
        f.add("ha", Sha512Hasher.hex(String.valueOf(payment.getAmount())));
        return f;
    }
}
