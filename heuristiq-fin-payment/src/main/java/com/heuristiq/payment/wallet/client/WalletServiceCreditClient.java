package com.heuristiq.payment.wallet.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import com.heuristiq.payment.config.WalletServiceProperties;
import com.heuristiq.payment.wallet.port.WalletTenantMissingException;
import com.heuristiq.payment.wallet.port.WalletCreditClient;
import com.heuristiq.payment.wallet.port.WalletCreditCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.RoundingMode;

/**
 * Adapter of {@link WalletCreditClient} over Rider_WalletService's {@code POST /internal/purchases} (service token).
 * The call carries an idempotency key derived from the top-up id, so it is {@link Idempotency#SAFE}: the resilient
 * caller may repeat it on a timeout, and the wallet returns the first journal for a repeat. The organization is sent
 * explicitly because the retry job has no signed-in user to propagate it from; the correlation id is forwarded by the
 * WebClient filter.
 */
@Component
public class WalletServiceCreditClient implements WalletCreditClient {

    private final WebClient webClient;
    private final ResilientCaller caller;
    private final WalletServiceProperties properties;
    private final ObjectMapper objectMapper;

    public WalletServiceCreditClient(@Qualifier("walletServiceWebClient") WebClient webClient,
                                     @Qualifier("walletResilientCaller") ResilientCaller caller,
                                     WalletServiceProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.caller = caller;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String credit(WalletCreditCommand command) {
        WalletPurchaseRequest body = new WalletPurchaseRequest(command.userId(),
                StringUtils.hasText(command.appCode()) ? command.appCode() : properties.getAppCode(),
                command.amount(),
                command.topupId());
        if (!StringUtils.hasText(command.tenantId())) {
            throw new WalletTenantMissingException("top-up " + command.topupId());
        }
        try {
            JsonNode response = caller.callBlocking(() -> webClient.post()
                    .uri(WalletClientConstants.PURCHASES_PATH)
                    .headers(headers -> {
                        headers.set(WalletClientConstants.IDEMPOTENCY_KEY_HEADER,
                                WalletClientConstants.IDEMPOTENCY_KEY_PREFIX + command.topupId());
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
