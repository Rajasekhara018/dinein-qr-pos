package com.heuristiq.payment.wallet.client;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Payment -> Wallet balance read: what is sent, how the answer maps, and that no failure ever looks like zero. */
class WalletServiceBalanceClientTest {

    private static final ResilienceProfile FAST = ResilienceProfile.defaults()
            .withCallTimeout(Duration.ofSeconds(2)).withMaxAttempts(3);

    private final List<ClientRequest> sent = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    private WalletServiceBalanceClient client(HttpStatus status, String body, HttpStatus... thenStatuses) {
        WebClient webClient = WebClient.builder().baseUrl("http://wallet.test/internal").exchangeFunction(request -> {
            sent.add(request);
            int n = calls.getAndIncrement();
            HttpStatus s = n == 0 || thenStatuses.length == 0 ? status : thenStatuses[Math.min(n - 1, thenStatuses.length - 1)];
            return Mono.just(ClientResponse.create(s).header("Content-Type", "application/json").body(body).build());
        }).build();
        return new WalletServiceBalanceClient(webClient, new ResilientCaller("wallet-test", FAST));
    }

    @Test
    void getsTheUsersBalanceInTheGivenOrganization() {
        WalletServiceBalanceClient c = client(HttpStatus.OK, "{\"success\":true,\"responseObject\":{\"balance\":\"125.50\"}}");

        assertThat(c.balanceOf("cust-1", "org-a")).isEqualTo(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("125.50"), "INR"));   // legacy string: the deployment currency
        ClientRequest request = sent.get(0);
        assertThat(request.method().name()).isEqualTo("GET");
        assertThat(request.url().getPath()).isEqualTo("/internal/users/cust-1/balance");
        assertThat(request.headers().getFirst("X-Tenant-Id")).isEqualTo("org-a");
    }

    @Test
    void aBalanceAsCodeAndValueKeepsItsCurrency() {
        assertThat(client(HttpStatus.OK, "{\"responseObject\":{\"balance\":{\"code\":\"AED\",\"value\":12.5}}}")
                .balanceOf("u", "t")).isEqualTo(com.heuristiq.payment.shared.money.Money.of(new BigDecimal("12.50"), "AED"));
    }

    @Test
    void withoutAnOrganizationNoTenantHeaderIsSet() {
        client(HttpStatus.OK, "{\"responseObject\":{\"balance\":\"0.00\"}}").balanceOf("cust-1", null);

        assertThat(sent.get(0).headers().getFirst("X-Tenant-Id")).isNull();
    }

    @Test
    void aZeroBalanceTheWalletReportsIsZero() {
        assertThat(client(HttpStatus.OK, "{\"responseObject\":{\"balance\":\"0.00\"}}").balanceOf("u", "t").value())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aTransient503_isRetried_thenSucceeds() {
        WalletServiceBalanceClient c = client(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"responseObject\":{\"balance\":\"7.00\"}}", HttpStatus.OK);

        assertThat(c.balanceOf("u", "t").value()).isEqualByComparingTo("7.00");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aPersistentOutage_isDownstreamUnavailable_neverZero() {
        WalletServiceBalanceClient c = client(HttpStatus.SERVICE_UNAVAILABLE, "");

        assertThatThrownBy(() -> c.balanceOf("u", "t")).isInstanceOf(DownstreamUnavailableException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aRefusal_isDownstreamUnavailable_andNotRetried() {
        for (HttpStatus refusal : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            calls.set(0);
            WalletServiceBalanceClient c = client(refusal, "{\"errorCode\":\"X\"}");

            assertThatThrownBy(() -> c.balanceOf("u", "t")).isInstanceOf(DownstreamUnavailableException.class);
            assertThat(calls.get()).as(refusal.toString()).isEqualTo(1);
        }
    }

    @Test
    void anUnreadableOrMissingBalance_isDownstreamUnavailable_neverZero() {
        for (String body : List.of("{\"success\":true}", "{\"responseObject\":{}}", "{\"responseObject\":{\"balance\":\"abc\"}}",
                "{\"responseObject\":{\"balance\":\"\"}}")) {
            assertThatThrownBy(() -> client(HttpStatus.OK, body).balanceOf("u", "t")).as(body)
                    .isInstanceOf(DownstreamUnavailableException.class);
        }
    }
}
