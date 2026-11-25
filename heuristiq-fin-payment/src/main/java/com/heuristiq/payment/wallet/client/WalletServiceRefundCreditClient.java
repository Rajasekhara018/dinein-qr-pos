package com.heuristiq.payment.wallet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import com.heuristiq.payment.config.WalletServiceProperties;
import com.heuristiq.payment.wallet.port.WalletTenantMissingException;
import com.heuristiq.payment.wallet.port.WalletRefundCreditClient;
import com.heuristiq.payment.wallet.port.WalletRefundCreditCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.RoundingMode;

/**
 * Adapter of {@link WalletRefundCreditClient} over Rider_WalletService's {@code POST /internal/refund-credits} (service
 * token). The idempotency key is derived from the refund id, so the call is {@link Idempotency#SAFE}: the resilient
 * caller may repeat it on a timeout and the wallet returns the first journal for a repeat. The organization is sent
 * explicitly (the refund's, not the caller's); the correlation id is forwarded by the WebClient filter.
 */
@Component
public class WalletServiceRefundCreditClient implements WalletRefundCreditClient {

    private final WebClient webClient;
    private final ResilientCaller caller;
    private final WalletServiceProperties properties;
    private final ObjectMapper objectMapper;

    public WalletServiceRefundCreditClient(@Qualifier("walletServiceWebClient") WebClient webClient,
                                           @Qualifier("walletResilientCaller") ResilientCaller caller,
                                           WalletServiceProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.caller = caller;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String credit(WalletRefundCreditCommand command) {
        WalletRefundCreditRequest body = new WalletRefundCreditRequest(command.userId(), properties.getAppCode(),
                command.amount(),
                command.paymentId(), command.refundId());
        if (!StringUtils.hasText(command.tenantId())) {
            throw new WalletTenantMissingException("refund " + command.refundId());
        }
        try {
            JsonNode response = caller.callBlocking(() -> webClient.post()
                    .uri(WalletClientConstants.REFUND_CREDITS_PATH)
                    .headers(headers -> {
                        headers.set(WalletClientConstants.IDEMPOTENCY_KEY_HEADER,
                                WalletClientConstants.REFUND_KEY_PREFIX + command.refundId());
                        headers.set(TenantIdPropagation.HEADER, command.tenantId());
                    })
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class), Idempotency.SAFE);
            return WalletResponses.journalIdOf(response);
        } catch (WebClientResponseException e) {
            throw WalletResponses.creditFailureOf(objectMapper, e);
        }
    }
}
