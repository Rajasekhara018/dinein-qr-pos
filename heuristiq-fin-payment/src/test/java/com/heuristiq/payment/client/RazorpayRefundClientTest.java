package com.heuristiq.payment.client;

import com.heuristiq.payment.config.RazorpayProperties;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.shared.exception.RefundOutcomeUnknownException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PAY-15: the refund call is bounded by a timeout; PAY-03: it is idempotent on the refund reference. */
class RazorpayRefundClientTest {

    private static RazorpayRefundClient client(ExchangeFunction exchange, long timeoutSeconds) {
        RazorpayProperties props = new RazorpayProperties();
        props.setRefundTimeoutSeconds(timeoutSeconds);
        return new RazorpayRefundClient(WebClient.builder().baseUrl("http://razorpay.test").exchangeFunction(exchange).build(), props);
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }

    @Test
    void aGatewayThatNeverAnswers_timesOut_asAnUnknownOutcome_insteadOfBlockingForever() {
        RazorpayRefundClient client = client(request -> Mono.never(), 1);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.refund("pay_1", 1000L, "r", "ref-1"))
                .isInstanceOf(RefundOutcomeUnknownException.class);

        assertThat((System.nanoTime() - start) / 1_000_000_000.0).isLessThan(10.0);
    }

    @Test
    void sendsTheReferenceAsTheGatewayIdempotencyHeader() {
        AtomicReference<ClientRequest> seen = new AtomicReference<>();
        RazorpayRefundClient client = client(request -> {
            seen.set(request);
            return Mono.just(json(HttpStatus.OK, "{\"id\":\"rfnd_1\",\"status\":\"processed\"}"));
        }, 5);

        var result = client.refund("pay_1", 1000L, "r", "ref-1");

        assertThat(result.refundId()).isEqualTo("rfnd_1");
        assertThat(seen.get().headers().getFirst(RefundClientConstants.IDEMPOTENCY_HEADER)).isEqualTo("ref-1");
        assertThat(seen.get().url().getPath()).isEqualTo("/payments/pay_1/refund");
    }

    @Test
    void gateway5xx_isAnUnknownOutcome() {
        RazorpayRefundClient client = client(request -> Mono.just(json(HttpStatus.BAD_GATEWAY, "{}")), 5);

        assertThatThrownBy(() -> client.refund("pay_1", 1000L, "r", "ref-1"))
                .isInstanceOf(RefundOutcomeUnknownException.class);
    }

    @Test
    void gateway4xx_isADefiniteRejection() {
        RazorpayRefundClient client = client(request -> Mono.just(json(HttpStatus.BAD_REQUEST, "{\"error\":\"nope\"}")), 5);

        assertThatThrownBy(() -> client.refund("pay_1", 1000L, "r", "ref-1"))
                .isInstanceOf(PaymentServiceException.class)
                .isNotInstanceOf(RefundOutcomeUnknownException.class);
    }

    @Test
    void paymentWithoutAProviderPaymentId_cannotBeRefunded() {
        RazorpayRefundClient client = client(request -> Mono.never(), 1);

        assertThatThrownBy(() -> client.refund(" ", 1000L, "r", "ref-1")).isInstanceOf(PaymentServiceException.class);
    }
}
