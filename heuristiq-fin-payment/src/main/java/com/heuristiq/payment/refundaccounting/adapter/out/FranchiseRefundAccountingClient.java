package com.heuristiq.payment.refundaccounting.adapter.out;

import com.heuristiq.payment.refundaccounting.domain.RefundAccounting;
import static com.heuristiq.payment.refundaccounting.domain.RefundAccountingConstants.*;
import com.heuristiq.resilient.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FranchiseRefundAccountingClient implements RefundAccounting.Gateway {
    private final WebClient client;
    private final ResilientCaller caller = new ResilientCaller("franchise-refund-accounting", ResilienceProfile.defaults());

    public FranchiseRefundAccountingClient(@Value("${franchise.service.url:${FRANCHISE_SERVICE_URL:http://localhost:9124}}") String url,
                                          @Value("${rider.internal.service-token}") String token) {
        client = WebClient.builder().baseUrl(url).defaultHeader("X-Rider-Service-Token", token)
                .filter(CorrelationIdPropagation.filter()).build();
    }

    @Override
    public void post(RefundAccounting.Posting p) {
        var payload = Map.of("tenantId", p.tenantId(), "eventId", p.eventId(), "refundEventId", p.refundEventId(),
                "amount", Map.of("code", p.currency(), "value", p.amount()), "creditAlreadyPosted", p.creditAlreadyPosted());
        JsonNode result = caller.callBlocking(() -> client.post().uri(POST_PATH)
                .header("X-Tenant-Id", p.tenantId()).header("Idempotency-Key", p.refundEventId())
                .bodyValue(payload).retrieve().bodyToMono(JsonNode.class), Idempotency.SAFE);
        if (result == null || !POSTED.equals(result.path("responseObject").path("wallet").asText())) {
            throw new IllegalStateException("Franchise has not confirmed the refund journal");
        }
    }
}
