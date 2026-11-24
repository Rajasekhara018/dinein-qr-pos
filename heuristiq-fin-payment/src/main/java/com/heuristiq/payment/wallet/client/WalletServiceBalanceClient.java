package com.heuristiq.paymentservice.wallet.client;

import com.heuristiq.paymentservice.shared.money.MoneyDefaults;
import com.heuristiq.paymentservice.shared.money.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import com.heuristiq.paymentservice.wallet.port.WalletBalanceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;

/**
 * Adapter of {@link WalletBalanceClient} over Rider_WalletService's {@code GET /internal/users/{userId}/balance}
 * (service token). A GET is {@link Idempotency#SAFE}, so the resilient caller may repeat it. Any answer that is not a
 * readable balance (4xx, 5xx, empty or malformed body) surfaces as {@link DownstreamUnavailableException}, so a caller
 * can never mistake a failure for a zero balance.
 */
@Component
public class WalletServiceBalanceClient implements WalletBalanceClient {

    private final WebClient webClient;
    private final ResilientCaller caller;

    public WalletServiceBalanceClient(@Qualifier("walletServiceWebClient") WebClient webClient,
                                      @Qualifier("walletResilientCaller") ResilientCaller caller) {
        this.webClient = webClient;
        this.caller = caller;
    }

    @Override
    public Money balanceOf(String userId, String tenantId) {
        JsonNode response;
        try {
            response = caller.callBlocking(() -> webClient.get()
                    .uri(WalletClientConstants.USER_BALANCE_PATH, userId)
                    .headers(headers -> {
                        if (StringUtils.hasText(tenantId)) {
                            headers.set(TenantIdPropagation.HEADER, tenantId);
                        }
                    })
                    .retrieve()
                    .bodyToMono(JsonNode.class), Idempotency.SAFE);
        } catch (WebClientResponseException e) {
            throw unreadable(e);
        }
        JsonNode balance = response == null ? null : response.path(WalletClientConstants.RESPONSE_OBJECT_FIELD)
                .path(WalletClientConstants.BALANCE_FIELD);
        // {code, value} from a current wallet; a bare number or string (the deployment's currency) from an older one.
        JsonNode value = balance != null && balance.isObject() ? balance.path(WalletClientConstants.MONEY_VALUE_FIELD) : balance;
        String code = balance != null && balance.isObject() ? balance.path(WalletClientConstants.MONEY_CODE_FIELD).asText(null) : null;
        String text = value == null || value.isMissingNode() || value.isNull() ? null : value.asText(null);
        if (!StringUtils.hasText(text)) {
            throw unreadable(null);
        }
        try {
            return Money.of(new BigDecimal(text.trim()), StringUtils.hasText(code) ? code : MoneyDefaults.currency());
        } catch (IllegalArgumentException e) {
            throw unreadable(e);
        }
    }

    private static DownstreamUnavailableException unreadable(Throwable cause) {
        return new DownstreamUnavailableException(WalletClientConstants.WALLET_SERVICE_NAME,
                DownstreamUnavailableException.Reason.FAILED, cause);
    }
}
