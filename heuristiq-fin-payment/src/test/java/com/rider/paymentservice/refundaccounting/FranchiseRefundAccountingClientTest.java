package com.heuristiq.paymentservice.refundaccounting;

import com.heuristiq.paymentservice.refundaccounting.adapter.out.FranchiseRefundAccountingClient;
import com.heuristiq.paymentservice.refundaccounting.domain.RefundAccounting;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FranchiseRefundAccountingClientTest {
    @Test
    void carriesOrganizationStableRefundAndCreditEvidenceAndRequiresTheWalletAcknowledgement() throws Exception {
        var body = new AtomicReference<String>();
        var tenant = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"success\":true,\"responseObject\":{\"wallet\":\"SALE_REFUNDED\"}}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/franchise/internal/ledger/payment-refunds", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            token.set(exchange.getRequestHeaders().getFirst("X-Rider-Service-Token"));
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new FranchiseRefundAccountingClient("http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-token");
            var posting = new RefundAccounting.Posting("payment-refund:1", "org-a", "ride-completed:1", new BigDecimal("50.00"), "INR", true, 0);
            client.post(posting);
            assertThat(tenant.get()).isEqualTo("org-a");
            assertThat(token.get()).isEqualTo("internal-test-token");
            assertThat(key.get()).isEqualTo("payment-refund:1");
            assertThat(body.get()).contains("\"creditAlreadyPosted\":true", "\"eventId\":\"ride-completed:1\"", "\"code\":\"INR\"");
            response.set("{\"success\":true,\"responseObject\":{\"wallet\":\"LEDGER_ONLY\"}}");
            assertThatThrownBy(() -> client.post(posting)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has not confirmed");
        } finally {
            server.stop(0);
        }
    }
}
