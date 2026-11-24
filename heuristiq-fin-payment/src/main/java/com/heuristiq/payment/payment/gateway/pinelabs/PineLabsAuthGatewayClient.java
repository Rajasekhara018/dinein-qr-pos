package com.heuristiq.payment.payment.gateway.pinelabs;

import com.heuristiq.payment.payment.gateway.GatewayClientConstants;
import com.heuristiq.payment.config.PineLabsGatewayProperties;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pine Labs requires a fresh OAuth-style access token (client_credentials grant) before EVERY
 * checkout/status call — unlike Razorpay's one-line Basic-auth header, there's a real
 * token-acquisition step. Shared by the checkout and status adapters so the token POST + response
 * parsing lives in one place. Ported from the reference design's PineLabsAuthClient.
 *
 * No token caching in this pass (the reference doesn't cache either — it requests a fresh token
 * per call) — kept simple and correct rather than adding cache-invalidation scope.
 */
@Slf4j
@Component
public class PineLabsAuthGatewayClient {


    private final WebClient webClient;
    private final PineLabsGatewayProperties props;

    public PineLabsAuthGatewayClient(WebClient paymentGatewayWebClient, PineLabsGatewayProperties props) {
        this.webClient = paymentGatewayWebClient;
        this.props = props;
    }

    public void assertConfigured() {
        if (!StringUtils.hasText(props.getClientId()) || !StringUtils.hasText(props.getClientSecret())) {
            throw new PaymentServiceException("Pine Labs integration is not configured yet");
        }
    }

    @SuppressWarnings("unchecked")
    public String generateAccessToken() {
        Map<String, Object> tokenRequest = new LinkedHashMap<>();
        tokenRequest.put("client_id", props.getClientId());
        tokenRequest.put("client_secret", props.getClientSecret());
        tokenRequest.put("grant_type", "client_credentials");

        Map<String, Object> body;
        try {
            body = webClient.post()
                    .uri(props.getBaseUrl() + PineLabsConstants.TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(tokenRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(GatewayClientConstants.CALL_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Unable to generate Pine Labs access token [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentServiceException("Unable to generate Pine Labs access token (" + e.getStatusCode() + ")");
        } catch (WebClientException e) {
            log.error("Pine Labs token endpoint unreachable: {}", e.getMessage());
            throw new PaymentServiceException("Pine Labs gateway unavailable. Please try again.");
        }

        Object token = body != null ? body.get("access_token") : null;
        if (token == null) {
            throw new PaymentServiceException("Pine Labs token endpoint returned no access_token");
        }
        return String.valueOf(token);
    }
}
