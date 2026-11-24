package com.heuristiq.payment.salesettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.payment.salesettlement.adapter.out.WalletSaleSettlementClient;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort.Answer;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The wallet call: path, headers, and how each answer is read. */
class WalletSaleSettlementClientTest {

    private final List<ClientRequest> sent = new ArrayList<>();

    private WalletSaleSettlementClient client(HttpStatus status, String body) {
        WebClient webClient = WebClient.builder().baseUrl("http://wallet/api/v1/wallet/internal")
                .exchangeFunction(request -> {
                    sent.add(request);
                    return Mono.just(ClientResponse.create(status).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body).build());
                }).build();
        return new WalletSaleSettlementClient(webClient, new ResilientCaller("wallet-test",
                com.heuristiq.resilient.ResilienceProfile.defaults().withMaxAttempts(1)), new ObjectMapper());
    }

    private static SaleSettlementRequest request(SaleSettlementAction action) {
        return new SaleSettlementRequest(1, "ride-completed:r-1", action,
                (action == SaleSettlementAction.SETTLE ? "sale-settle:" : "sale-cancel:") + "ride-completed:r-1", "p-1", "t1",
                action == SaleSettlementAction.CANCEL ? "Gateway payment failed" : null, 0);
    }

    @Test
    void aSettlementPostsToTheSalesSettlePathWithKeyAndOrganization() {
        Answer answer = client(HttpStatus.OK, "{\"responseObject\":{\"journalId\":\"j-1\"}}").send(request(SaleSettlementAction.SETTLE));

        assertThat(answer.result()).isEqualTo(Result.DONE);
        assertThat(answer.journalId()).isEqualTo("j-1");
        ClientRequest call = sent.get(0);
        assertThat(java.net.URLDecoder.decode(call.url().getPath(), StandardCharsets.UTF_8))
                .isEqualTo("/api/v1/wallet/internal/sales/ride-completed:r-1/settle");
        assertThat(call.headers().getFirst("Idempotency-Key")).isEqualTo("sale-settle:ride-completed:r-1");
        assertThat(call.headers().getFirst("X-Tenant-Id")).isEqualTo("t1");
    }

    @Test
    void aCancellationPostsToTheCancelPath() {
        client(HttpStatus.OK, "{\"responseObject\":{\"journalId\":\"rev\"}}").send(request(SaleSettlementAction.CANCEL));

        assertThat(sent.get(0).url().getPath()).endsWith("/cancel");
        assertThat(sent.get(0).headers().getFirst("Idempotency-Key")).isEqualTo("sale-cancel:ride-completed:r-1");
    }

    @Test
    void eachWalletAnswerIsClassified() {
        assertThat(client(HttpStatus.NOT_FOUND, "{\"errorCode\":\"NOT_FOUND\"}").send(request(SaleSettlementAction.SETTLE)).result())
                .isEqualTo(Result.SALE_NOT_FOUND);
        assertThat(client(HttpStatus.CONFLICT, "{\"errorCode\":\"SALE_NOT_PENDING\"}").send(request(SaleSettlementAction.SETTLE)).result())
                .isEqualTo(Result.NOT_PENDING);
        assertThat(client(HttpStatus.CONFLICT, "{\"errorCode\":\"BUSY\"}").send(request(SaleSettlementAction.SETTLE)).result())
                .isEqualTo(Result.UNAVAILABLE);
        Answer refused = client(HttpStatus.BAD_REQUEST, "{\"errorCode\":\"VALIDATION_ERROR\"}").send(request(SaleSettlementAction.SETTLE));
        assertThat(refused.result()).isEqualTo(Result.REJECTED);
        assertThat(refused.detail()).isEqualTo("VALIDATION_ERROR");
        assertThat(client(HttpStatus.SERVICE_UNAVAILABLE, "not json").send(request(SaleSettlementAction.SETTLE)).result())
                .isEqualTo(Result.UNAVAILABLE);
    }

    @Test
    void theLookupAsksForTheSalesOfOneOrganizationAndReadsTheBookedEventIds() {
        var booked = client(HttpStatus.OK, "{\"responseObject\":[{\"eventId\":\"e-1\",\"status\":\"POSTED\"},{\"eventId\":\"\"},{}]}")
                .bookedSales("org-a", List.of("e-1", "e-2"));

        assertThat(booked).contains(java.util.Set.of("e-1"));
        ClientRequest call = sent.get(0);
        assertThat(call.method().name()).isEqualTo("POST");
        assertThat(call.url().getPath()).isEqualTo("/api/v1/wallet/internal/sales/lookups");
        assertThat(call.headers().getFirst("X-Tenant-Id")).isEqualTo("org-a");
    }

    @Test
    void aLookupOfNoSalesAsksNothing() {
        assertThat(client(HttpStatus.OK, "{}").bookedSales("org-a", List.of())).contains(java.util.Set.of());
        assertThat(sent).isEmpty();
    }

    @Test
    void aLookupTheWalletCannotAnswerIsUnknown() {
        assertThat(client(HttpStatus.BAD_REQUEST, "{\"errorCode\":\"VALIDATION_ERROR\"}").bookedSales("t", List.of("e"))).isEmpty();
        assertThat(client(HttpStatus.SERVICE_UNAVAILABLE, "down").bookedSales("t", List.of("e"))).isEmpty();
        assertThat(client(HttpStatus.OK, "{\"responseObject\":{\"eventId\":\"e\"}}").bookedSales("t", List.of("e")))
                .as("an answer that is not a list").isEmpty();
    }
}
