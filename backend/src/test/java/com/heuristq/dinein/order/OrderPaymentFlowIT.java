package com.heuristq.dinein.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.payment.PaymentExpiryJob;
import com.heuristq.dinein.payment.domain.PaymentEventRepository;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPaymentFlowIT extends AbstractIntegrationTest {

    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderQueryService orderQueryService;
    @Autowired
    PaymentEventRepository paymentEventRepository;
    @Autowired
    PaymentExpiryJob expiryJob;

    /** Places the standard cart and returns the checkout response. */
    private JsonNode checkout(Cookie guest) throws Exception {
        MvcResult result = placeOrder(guest, UUID.randomUUID().toString(), simpleCart());
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    private void stubCapturedPayment(String rzpOrderId, String paymentId, long amountPaise) {
        doReturn(new ProviderPayment(rzpOrderId, paymentId, ProviderPayment.Outcome.CAPTURED, amountPaise, "upi", null))
                .when(razorpay).fetchPayment(rzpOrderId, paymentId);
    }

    private MockHttpServletRequestBuilder verifyRequest(Cookie guest, String rzpOrderId, String paymentId) {
        return post("/api/v1/public/payments/verify").with(csrf()).cookie(guest).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"razorpay_order_id":"%s","razorpay_payment_id":"%s","razorpay_signature":"%s"}"""
                        .formatted(rzpOrderId, paymentId, paymentSignature(rzpOrderId, paymentId)));
    }

    private MockHttpServletRequestBuilder webhook(String payload, String eventId) {
        return post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(payload)
                .header("X-Razorpay-Signature", webhookSignature(payload))
                .header("X-Razorpay-Event-Id", eventId);
    }

    @Test
    void serverRecomputesTotalsFromTheMenu() throws Exception {
        JsonNode checkout = checkout(guestCookie());
        // (240 + 20) x 2 = 520 and Full biryani 260 -> 780 subtotal, 5% GST = 39.00 -> 819.00
        assertThat(checkout.get("amountPaise").asLong()).isEqualTo(81900L);
        assertThat(checkout.get("mode").asText()).isEqualTo("SDK");
        assertThat(checkout.get("checkout").get("key").asText()).isEqualTo("rzp_test_dummy");
        assertThat(checkout.toString()).doesNotContain(KEY_SECRET);

        OrderEntity order = orderRepository.findById(checkout.get("orderId").asLong()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getGrandTotal()).isEqualByComparingTo("819.00");
        assertThat(order.getOrderNumber()).matches("\\d{6}-\\d{3}");
    }

    @Test
    void placeOrderThenVerifyConfirmsAndShowsInKitchen() throws Exception {
        Cookie guest = guestCookie();
        JsonNode checkout = checkout(guest);
        long orderId = checkout.get("orderId").asLong();
        String rzpOrderId = checkout.get("checkout").get("order_id").asText();

        assertThat(kitchenIds()).doesNotContain(orderId);

        stubCapturedPayment(rzpOrderId, "pay_ok1", 81900L);
        mvc.perform(verifyRequest(guest, rzpOrderId, "pay_ok1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.method").value("upi"));

        assertThat(kitchenIds()).contains(orderId);
    }

    @Test
    void tamperedClientSignatureIsRejected() throws Exception {
        Cookie guest = guestCookie();
        String rzpOrderId = checkout(guest).get("checkout").get("order_id").asText();

        mvc.perform(post("/api/v1/public/payments/verify").with(csrf()).cookie(guest).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpay_order_id":"%s","razorpay_payment_id":"pay_x","razorpay_signature":"%s"}"""
                                .formatted(rzpOrderId, paymentSignature(rzpOrderId, "pay_other"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    void sameIdempotencyKeyNeverCreatesADuplicateOrder() throws Exception {
        Cookie guest = guestCookie();
        String key = UUID.randomUUID().toString();

        JsonNode first = body(placeOrder(guest, key, simpleCart()));
        JsonNode second = body(placeOrder(guest, key, simpleCart()));

        assertThat(second.get("orderId").asLong()).isEqualTo(first.get("orderId").asLong());
        assertThat(second.get("checkout").get("order_id").asText()).isEqualTo(first.get("checkout").get("order_id").asText());
        verify(razorpay, times(1)).createProviderOrder(any());
    }

    @Test
    void duplicateWebhookIsAcknowledgedButProcessedOnce() throws Exception {
        JsonNode checkout = checkout(guestCookie());
        long orderId = checkout.get("orderId").asLong();
        String payload = capturedWebhook(checkout.get("checkout").get("order_id").asText(), "pay_dup", 81900L);
        String eventId = "evt_" + UUID.randomUUID();

        mvc.perform(webhook(payload, eventId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
        Instant paidAt = orderRepository.findById(orderId).orElseThrow().getPaidAt();
        mvc.perform(webhook(payload, eventId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(paymentEventRepository.findAll().stream().filter(e -> eventId.equals(e.getProviderEventId()))).hasSize(1);
    }

    @Test
    void webhookBeforeClientVerifyIsIdempotent() throws Exception {
        Cookie guest = guestCookie();
        JsonNode checkout = checkout(guest);
        long orderId = checkout.get("orderId").asLong();
        String rzpOrderId = checkout.get("checkout").get("order_id").asText();

        mvc.perform(webhook(capturedWebhook(rzpOrderId, "pay_wh1", 81900L), "evt_" + UUID.randomUUID()))
                .andExpect(status().isOk());
        stubCapturedPayment(rzpOrderId, "pay_wh1", 81900L);
        mvc.perform(verifyRequest(guest, rzpOrderId, "pay_wh1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().isPaymentFlagged()).isFalse();
    }

    @Test
    void clientVerifyBeforeWebhookIsIdempotent() throws Exception {
        Cookie guest = guestCookie();
        JsonNode checkout = checkout(guest);
        long orderId = checkout.get("orderId").asLong();
        String rzpOrderId = checkout.get("checkout").get("order_id").asText();

        stubCapturedPayment(rzpOrderId, "pay_cv1", 81900L);
        mvc.perform(verifyRequest(guest, rzpOrderId, "pay_cv1")).andExpect(status().isOk());
        Instant paidAt = orderRepository.findById(orderId).orElseThrow().getPaidAt();
        mvc.perform(webhook(capturedWebhook(rzpOrderId, "pay_cv1", 81900L), "evt_" + UUID.randomUUID()))
                .andExpect(status().isOk());

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(order.isPaymentFlagged()).isFalse();
    }

    @Test
    void amountMismatchFlagsOrderAndKeepsItOutOfTheKitchen() throws Exception {
        JsonNode checkout = checkout(guestCookie());
        long orderId = checkout.get("orderId").asLong();

        mvc.perform(webhook(capturedWebhook(checkout.get("checkout").get("order_id").asText(), "pay_short", 100L),
                "evt_" + UUID.randomUUID())).andExpect(status().isOk());

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.isPaymentFlagged()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(kitchenIds()).doesNotContain(orderId);
    }

    @Test
    void invalidWebhookSignatureIsRejectedWith400() throws Exception {
        String payload = capturedWebhook("order_x", "pay_x", 100L);
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Razorpay-Signature", "0".repeat(64)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableItemIsRejectedWithOffendingLines() throws Exception {
        paneer.setAvailable(false);
        itemRepository.save(paneer);

        mvc.perform(post("/api/v1/public/orders").with(csrf()).cookie(guestCookie())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(simpleCart()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.details[0].itemId").value(paneer.getId()))
                .andExpect(jsonPath("$.details[0].reason").value("ITEM_UNAVAILABLE"))
                .andExpect(jsonPath("$.details.length()").value(1));
    }

    @Test
    void menuPriceChangeDoesNotAlterExistingOrders() throws Exception {
        Cookie guest = guestCookie();
        long orderId = checkout(guest).get("orderId").asLong();

        paneer.setBasePrice(new BigDecimal("999.00"));
        itemRepository.save(paneer);

        mvc.perform(get("/api/v1/public/orders/" + orderId).cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(260.00))
                .andExpect(jsonPath("$.bill.grandTotal").value(819.00));
    }

    @Test
    void guestsCannotReadOtherGuestsOrders() throws Exception {
        long orderId = checkout(guestCookie()).get("orderId").asLong();

        mvc.perform(get("/api/v1/public/orders/" + orderId).cookie(guestCookie())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/orders/" + orderId)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiryJobConfirmsDelayedCaptureInsteadOfExpiring() throws Exception {
        JsonNode checkout = checkout(guestCookie());
        long orderId = checkout.get("orderId").asLong();
        String rzpOrderId = checkout.get("checkout").get("order_id").asText();
        backdate(orderId);
        doReturn(List.of(new ProviderPayment(rzpOrderId, "pay_late", ProviderPayment.Outcome.CAPTURED, 81900L, "card", null)))
                .when(razorpay).fetchOrderPayments(eq(rzpOrderId));

        expiryJob.run();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void expiryJobExpiresUnpaidOrders() throws Exception {
        long orderId = checkout(guestCookie()).get("orderId").asLong();
        backdate(orderId);
        doReturn(List.of()).when(razorpay).fetchOrderPayments(anyString());

        expiryJob.run();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    private void backdate(long orderId) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setPlacedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        orderRepository.save(order);
    }

    /**
     * Calls the service directly (bypassing HTTP), so it must simulate the staff context TokenAuthenticationFilter
     * would normally populate from the JWT — kitchenOrders() is restaurant-scoped and requires one.
     */
    private List<Long> kitchenIds() {
        StaffPrincipal principal = new StaffPrincipal(0L, "test", StaffRole.OWNER, RestaurantEntity.DEFAULT_ID, null, false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
        try {
            return orderQueryService.kitchenOrders(OrderStatus.KITCHEN_VISIBLE).stream().map(KitchenOrderView::id).toList();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
