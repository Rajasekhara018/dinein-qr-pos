package com.heuristiq.payment.salesettlement.adapter.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementConstants;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort;
import com.heuristiq.payment.wallet.client.WalletClientConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter of {@link WalletSaleSettlementPort} over Rider_WalletService's {@code POST /internal/sales/{eventId}/settle} and
 * {@code /cancel} (service token, organization in {@code X-Tenant-Id}). The call carries the request's idempotency key, so the
 * resilient caller may repeat it ({@link Idempotency#SAFE}). {@link #bookedSales} reads {@code POST /internal/sales/lookups}, which
 * answers the booked SALE journals among the event ids asked (an event not booked is simply absent).
 */
@Component
public class WalletSaleSettlementClient implements WalletSaleSettlementPort {

    private static final Logger log = LoggerFactory.getLogger(WalletSaleSettlementClient.class);

    static final String SETTLE_PATH = "/sales/{eventId}/settle";
    static final String CANCEL_PATH = "/sales/{eventId}/cancel";
    static final String REASON_FIELD = "reason";
    static final String LOOKUPS_PATH = "/sales/lookups";
    static final String EVENT_IDS_FIELD = "eventIds";
    static final String EVENT_ID_FIELD = "eventId";

    private final WebClient webClient;
    private final ResilientCaller caller;
    private final ObjectMapper objectMapper;

    public WalletSaleSettlementClient(@Qualifier("walletServiceWebClient") WebClient webClient,
                                      @Qualifier("walletResilientCaller") ResilientCaller caller, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.caller = caller;
        this.objectMapper = objectMapper;
    }

    @Override
    public Answer send(SaleSettlementRequest request) {
        boolean settle = request.action() == SaleSettlementAction.SETTLE;
        try {
            JsonNode response = caller.callBlocking(() -> {
                WebClient.RequestBodySpec spec = webClient.post()
                        .uri(settle ? SETTLE_PATH : CANCEL_PATH, request.eventId())
                        .headers(headers -> {
                            headers.set(WalletClientConstants.IDEMPOTENCY_KEY_HEADER, request.idempotencyKey());
                            headers.set(TenantIdPropagation.HEADER, request.tenantId());
                        });
                WebClient.RequestHeadersSpec<?> ready = settle ? spec
                        : spec.bodyValue(Map.of(REASON_FIELD, request.reason() == null ? SaleSettlementConstants.REASON_FAILED
                        : request.reason()));
                return ready.retrieve().bodyToMono(JsonNode.class);
            }, Idempotency.SAFE);
            return new Answer(Result.DONE, journalIdOf(response), null);
        } catch (WebClientResponseException e) {
            return classify(e);
        } catch (DownstreamUnavailableException e) {
            return new Answer(Result.UNAVAILABLE, null, SaleSettlementConstants.ERROR_WALLET_UNAVAILABLE);
        }
    }

    @Override
    public Optional<Set<String>> bookedSales(String tenantId, Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Optional.of(Set.of());
        }
        try {
            JsonNode response = caller.callBlocking(() -> webClient.post()
                    .uri(LOOKUPS_PATH)
                    .headers(headers -> headers.set(TenantIdPropagation.HEADER, tenantId))
                    .bodyValue(Map.of(EVENT_IDS_FIELD, List.copyOf(eventIds)))
                    .retrieve()
                    .bodyToMono(JsonNode.class), Idempotency.SAFE);
            JsonNode sales = response == null ? null : response.path(WalletClientConstants.RESPONSE_OBJECT_FIELD);
            if (sales == null || !sales.isArray()) {
                log.warn("Wallet sale lookup answered without a list; the settlements wait");
                return Optional.empty();
            }
            Set<String> booked = new HashSet<>();
            for (JsonNode sale : sales) {
                String id = sale.path(EVENT_ID_FIELD).asText(null);
                if (id != null && !id.isBlank()) {
                    booked.add(id);
                }
            }
            return Optional.of(booked);
        } catch (WebClientResponseException e) {
            log.warn("Wallet sale lookup refused with HTTP {} ({}); the settlements are retried", e.getStatusCode().value(),
                    errorCodeOf(e));
            return Optional.empty();
        } catch (DownstreamUnavailableException e) {
            log.warn("Wallet sale lookup unavailable; the settlements are retried: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 404 no sale; 409 SALE_NOT_PENDING done already; 400 refused as such; anything else unknown. */
    Answer classify(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = errorCodeOf(e);
        if (status == 404) {
            return new Answer(Result.SALE_NOT_FOUND, null, SaleSettlementConstants.ERROR_SALE_NOT_FOUND);
        }
        if (status == 409 && SaleSettlementConstants.CODE_SALE_NOT_PENDING.equals(code)) {
            return new Answer(Result.NOT_PENDING, null, code);
        }
        if (status == 400) {
            return new Answer(Result.REJECTED, null, code == null ? "HTTP_400" : code);
        }
        return new Answer(Result.UNAVAILABLE, null, code == null ? "HTTP_" + status : code);
    }

    private String errorCodeOf(WebClientResponseException e) {
        try {
            String code = objectMapper.readTree(e.getResponseBodyAsString()).path(WalletClientConstants.ERROR_CODE_FIELD).asText();
            return code == null || code.isBlank() ? null : code;
        } catch (IOException | RuntimeException notJson) {
            return null;
        }
    }

    private static String journalIdOf(JsonNode response) {
        if (response == null) {
            return null;
        }
        String id = response.path(WalletClientConstants.RESPONSE_OBJECT_FIELD).path(WalletClientConstants.JOURNAL_ID_FIELD).asText(null);
        return id == null || id.isBlank() ? null : id;
    }
}
