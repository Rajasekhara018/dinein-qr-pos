package com.heuristiq.paymentservice.wallet.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.paymentservice.config.WalletServiceProperties;
import com.heuristiq.paymentservice.wallet.port.WalletCreditCommand;
import com.heuristiq.paymentservice.wallet.port.WalletCreditRejectedException;
import com.heuristiq.paymentservice.wallet.port.WalletTenantMissingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Payment -> Wallet credit call: what is sent (key, organization, body), how answers map, what is retried. */
class WalletServiceCreditClientTest {

    private static final ResilienceProfile FAST = ResilienceProfile.defaults()
            .withCallTimeout(Duration.ofSeconds(2)).withMaxAttempts(3);
    private static final WalletCreditCommand COMMAND =
            new WalletCreditCommand("topup-1", "cust-1", "org-a", com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("500.5"), "INR"));

    private final List<ClientRequest> sent = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private String lastBody;

    private WalletServiceCreditClient client(HttpStatus status, String body, HttpStatus... thenStatuses) {
        WebClient webClient = WebClient.builder().baseUrl("http://wallet.test/internal").exchangeFunction(request -> {
            sent.add(request);
            int n = calls.getAndIncrement();
            if (n == 0) {
                lastBody = render(request);   // first attempt runs on the test thread; retries run on a scheduler thread
            }
            HttpStatus s = n == 0 || thenStatuses.length == 0 ? status : thenStatuses[Math.min(n - 1, thenStatuses.length - 1)];
            return Mono.just(ClientResponse.create(s).header("Content-Type", "application/json")
                    .body(s.is2xxSuccessful() ? body : "{\"errorCode\":\"PURCHASE_DISABLED\"}").build());
        }).build();
        return new WalletServiceCreditClient(webClient, new ResilientCaller("wallet-test", FAST),
                new WalletServiceProperties(), new ObjectMapper());
    }

    private static String render(ClientRequest request) {
        MockClientHttpRequest out = new MockClientHttpRequest(HttpMethod.POST, "/");
        request.body().insert(out, new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return out.getBodyAsString().block();
    }

    @Test
    void sendsOneKeyedPurchase_withTheTopupIdAmountAndOrganization_andReturnsTheJournalId() {
        String journal = client(HttpStatus.OK, "{\"success\":true,\"responseObject\":{\"journalId\":\"j-7\"}}").credit(COMMAND);

        assertThat(journal).isEqualTo("j-7");
        ClientRequest request = sent.get(0);
        assertThat(request.method().name()).isEqualTo("POST");
        assertThat(request.url().getPath()).isEqualTo("/internal/purchases");
        assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo("PURCHASE:topup-1");
        assertThat(request.headers().getFirst("X-Tenant-Id")).isEqualTo("org-a");
    }

    @Test
    void bodyCarriesUserAppCodeTwoDecimalAmountAndThePaymentId() {
        client(HttpStatus.OK, "{\"responseObject\":{}}").credit(COMMAND);

        assertThat(lastBody).contains("\"userId\":\"cust-1\"", "\"appCode\":\"BESTRIDE_RIDER_APP\"",
                "\"amount\":{\"code\":\"INR\",\"value\":500.50}", "\"paymentId\":\"topup-1\"");
    }

    @Test
    void purchaseUsesThePersistedOriginAppInsteadOfTheDeploymentDefault() {
        client(HttpStatus.OK, "{\"responseObject\":{\"journalId\":\"j-origin\"}}")
                .credit(new WalletCreditCommand(COMMAND.topupId(), COMMAND.userId(), COMMAND.tenantId(), COMMAND.amount(),
                        "PASUPU_KUMKUMA_MATRIMONY_APP"));
        assertThat(lastBody).contains("\"appCode\":\"PASUPU_KUMKUMA_MATRIMONY_APP\"");
    }

    @Test
    void anAnswerWithoutAJournalId_isNullNotAnError() {
        assertThat(client(HttpStatus.OK, "{\"success\":true}").credit(COMMAND)).isNull();
    }

    @Test
    void a409_isARefusalWithTheWalletsErrorCode_andIsNotRetried() {
        WalletServiceCreditClient c = client(HttpStatus.CONFLICT, "");

        assertThatThrownBy(() -> c.credit(COMMAND)).isInstanceOfSatisfying(WalletCreditRejectedException.class, e -> {
            assertThat(e.httpStatus()).isEqualTo(409);
            assertThat(e.errorCode()).isEqualTo("PURCHASE_DISABLED");
        });
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void aTransient503_isRetriedWithTheSameKey_thenSucceeds() {
        WalletServiceCreditClient c = client(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"responseObject\":{\"journalId\":\"j-2\"}}", HttpStatus.OK);

        assertThat(c.credit(COMMAND)).isEqualTo("j-2");
        assertThat(calls.get()).isEqualTo(2);
        assertThat(sent).extracting(r -> r.headers().getFirst("Idempotency-Key")).containsOnly("PURCHASE:topup-1");
    }

    @Test
    void aPersistentOutage_becomesDownstreamUnavailable() {
        WalletServiceCreditClient c = client(HttpStatus.SERVICE_UNAVAILABLE, "");

        assertThatThrownBy(() -> c.credit(COMMAND)).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aBlankOrganization_failsTheCallBeforeAnyRequestIsSent() {
        WalletServiceCreditClient c = client(HttpStatus.OK, "{\"responseObject\":{\"journalId\":\"j\"}}");

        assertThatThrownBy(() -> c.credit(new WalletCreditCommand("t", "cust-1", null, com.heuristiq.paymentservice.shared.money.Money.of(BigDecimal.TEN, "INR")))).isInstanceOf(WalletTenantMissingException.class);
        assertThatThrownBy(() -> c.credit(new WalletCreditCommand("t", "cust-1", "  ", com.heuristiq.paymentservice.shared.money.Money.of(BigDecimal.TEN, "INR")))).isInstanceOf(WalletTenantMissingException.class);
        assertThat(calls.get()).isZero();
        assertThat(sent).isEmpty();
    }

    @Test
    void onlyDefiniteBusinessRefusalsAreRefusals() {
        assertThatThrownBy(() -> statusClient(HttpStatus.BAD_REQUEST, "{\"errorCode\":\"VALIDATION_ERROR\"}").credit(COMMAND))
                .isInstanceOf(WalletCreditRejectedException.class);
        for (String code : new String[]{"APP_NOT_ENABLED", "PURCHASE_DISABLED", "ACCOUNT_FROZEN"}) {
            assertThatThrownBy(() -> statusClient(HttpStatus.CONFLICT, "{\"errorCode\":\"" + code + "\"}").credit(COMMAND))
                    .as(code).isInstanceOfSatisfying(WalletCreditRejectedException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(code));
        }
    }

    @Test
    void everyOtherOutcomeIsUnknown_notARefusal() {
        Object[][] cases = {
                {HttpStatus.UNAUTHORIZED, "nope"}, {HttpStatus.FORBIDDEN, "{\"errorCode\":\"FORBIDDEN\"}"},
                {HttpStatus.NOT_FOUND, ""}, {HttpStatus.REQUEST_TIMEOUT, ""}, {HttpStatus.TOO_MANY_REQUESTS, ""},
                {HttpStatus.BAD_REQUEST, "not json"},
                {HttpStatus.CONFLICT, "{\"errorCode\":\"BUSY\"}"},
                {HttpStatus.CONFLICT, "{\"errorCode\":\"IDEMPOTENCY_CONFLICT\"}"},
                {HttpStatus.CONFLICT, "{\"errorCode\":\"VALIDATION_ERROR\"}"},
                {HttpStatus.INTERNAL_SERVER_ERROR, "{\"errorCode\":\"APP_NOT_ENABLED\"}"}};
        for (Object[] c : cases) {
            assertThatThrownBy(() -> statusClient((HttpStatus) c[0], (String) c[1]).credit(COMMAND))
                    .as(c[0] + " " + c[1]).isInstanceOf(DownstreamUnavailableException.class)
                    .isNotInstanceOf(WalletCreditRejectedException.class);
        }
    }

    private WalletServiceCreditClient statusClient(HttpStatus status, String body) {
        WebClient webClient = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(status).header("Content-Type", "application/json").body(body).build())).build();
        return new WalletServiceCreditClient(webClient, new ResilientCaller("w", FAST), new WalletServiceProperties(), new ObjectMapper());
    }
}
