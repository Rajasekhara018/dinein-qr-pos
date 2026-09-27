package com.heuristq.dinein.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.domain.OrderType;
import com.heuristq.dinein.payment.PaymentExpiryJob;
import com.heuristq.dinein.payment.domain.PaymentEntity;
import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.payment.domain.PaymentStatus;
import com.heuristq.dinein.payment.domain.RefundStatus;
import com.heuristq.dinein.payment.gateway.ProviderPayment;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Waiter screen, staff-assisted orders, offline payments, order types and the related reports. */
class WaiterFlowIT extends AbstractIntegrationTest {

    @Autowired
    StaffUserRepository staffUserRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RestaurantSettingsRepository settingsRepository;
    @Autowired
    PaymentExpiryJob expiryJob;

    // ----- helpers -------------------------------------------------------------------------------

    private StaffUserEntity createUser(StaffRole role) {
        StaffUserEntity user = new StaffUserEntity();
        user.setUsername("w" + UUID.randomUUID().toString().substring(0, 8));
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode("Secret123"));
        user.setPinHash(passwordEncoder.encode("2468"));
        staffUserRepository.save(user);
        return user;
    }

    private String token(StaffRole role) throws Exception {
        StaffUserEntity user = createUser(role);
        if (role == StaffRole.KITCHEN) {
            MvcResult device = mvc.perform(post("/api/v1/auth/kitchen-device").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"pin\":\"2468\"}".formatted(user.getUsername()))).andReturn();
            return "Bearer " + body(device).get("deviceToken").asText();
        }
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"Secret123\"}".formatted(user.getUsername()))).andReturn();
        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
        return "Bearer " + body(login).get("accessToken").asText();
    }

    private String staffCart(Long tableId, String orderType, String paymentMethod, String key) {
        return """
                {"tableId":%s,"orderType":"%s","paymentMethod":"%s","idempotencyKey":"%s","note":"no onion",
                 "items":[{"itemId":%d,"addonIds":[%d],"quantity":2},
                          {"itemId":%d,"variantId":%d,"quantity":1}]}
                """.formatted(tableId == null ? "null" : tableId.toString(), orderType, paymentMethod, key,
                paneer.getId(), paneer.getAddons().getFirst().getId(), biryani.getId(),
                biryani.getVariants().get(1).getId());
    }

    private JsonNode placeStaffOrder(String auth, String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    private long cashOrder(String waiter) throws Exception {
        return placeStaffOrder(waiter, "/api/v1/waiter/orders",
                staffCart(table.getId(), "DINE_IN", "CASH", UUID.randomUUID().toString())).get("orderId").asLong();
    }

    private void kitchenStatus(String auth, long orderId, String status) throws Exception {
        mvc.perform(patch("/api/v1/kitchen/orders/" + orderId + "/status").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private void setTakeaway(boolean enabled) {
        RestaurantSettingsEntity s = settingsRepository.findById(RestaurantSettingsEntity.SINGLETON_ID).orElseThrow();
        s.setTakeawayEnabled(enabled);
        settingsRepository.save(s);
    }

    // ----- roles ---------------------------------------------------------------------------------

    @Test
    void waiterEndpointsAreForWaitersManagersAndOwnersOnly() throws Exception {
        String waiter = token(StaffRole.WAITER);
        mvc.perform(get("/api/v1/waiter/config").header("Authorization", waiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.role").value("WAITER"))
                .andExpect(jsonPath("$.takeawayEnabled").isBoolean())
                .andExpect(jsonPath("$.onlinePaymentsAvailable").value(true));
        mvc.perform(get("/api/v1/waiter/menu").header("Authorization", waiter)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", waiter)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/kitchen/orders").header("Authorization", waiter)).andExpect(status().isForbidden());

        String kitchen = token(StaffRole.KITCHEN);
        mvc.perform(get("/api/v1/waiter/tables").header("Authorization", kitchen)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/waiter/orders")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/waiter/orders").header("Authorization", token(StaffRole.MANAGER))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/waiter/orders").header("Authorization", token(StaffRole.OWNER))).andExpect(status().isOk());
    }

    // ----- staff-assisted orders -----------------------------------------------------------------

    @Test
    void cashOrderFromTheWaiterGoesStraightToTheKitchen() throws Exception {
        String waiter = token(StaffRole.WAITER);
        String key = UUID.randomUUID().toString();
        JsonNode placed = placeStaffOrder(waiter, "/api/v1/waiter/orders", staffCart(table.getId(), "DINE_IN", "CASH", key));

        long orderId = placed.get("orderId").asLong();
        assertThat(placed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(placed.get("provider").asText()).isEqualTo("OFFLINE");
        assertThat(placed.get("amountPaise").asLong()).isEqualTo(81900L);
        assertThat(placed.has("mode")).isFalse();
        verify(razorpay, never()).createProviderOrder(any());

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getGuestSessionId()).isNull();
        assertThat(order.getPlacedByStaffId()).isNotNull();
        assertThat(order.getOrderType()).isEqualTo(OrderType.DINE_IN);
        assertThat(order.getNotes()).isEqualTo("no onion");
        assertThat(order.getPaidAt()).isNotNull();
        PaymentEntity payment = paymentRepository.findByOrderIdOrderByIdAsc(orderId).getFirst();
        assertThat(payment.getProvider()).isEqualTo("OFFLINE");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getMethod()).isEqualTo("CASH");
        assertThat(payment.getRecordedByStaffId()).isEqualTo(order.getPlacedByStaffId());

        // Visible to the kitchen, with type and table.
        JsonNode kitchen = body(mvc.perform(get("/api/v1/kitchen/orders").header("Authorization", token(StaffRole.KITCHEN)))
                .andExpect(status().isOk()).andReturn());
        JsonNode ticket = find(kitchen, orderId);
        assertThat(ticket.get("orderType").asText()).isEqualTo("DINE_IN");
        assertThat(ticket.get("tableLabel").asText()).isEqualTo(table.getLabel());
        assertThat(ticket.get("placedByStaff").asBoolean()).isTrue();

        // And on the waiter's table overview.
        JsonNode tables = body(mvc.perform(get("/api/v1/waiter/tables").header("Authorization", waiter)).andReturn());
        JsonNode row = null;
        for (JsonNode t : tables) {
            if (t.get("id").asLong() == table.getId()) {
                row = t;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("confirmed").asInt()).isEqualTo(1);
        assertThat(row.get("openOrders").asInt()).isEqualTo(1);

        // Repeating the idempotency key returns the same order.
        JsonNode replay = placeStaffOrder(waiter, "/api/v1/waiter/orders", staffCart(table.getId(), "DINE_IN", "CASH", key));
        assertThat(replay.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId)).hasSize(1);
    }

    @Test
    void dineInStaffOrderNeedsATable() throws Exception {
        mvc.perform(post("/api/v1/waiter/orders").header("Authorization", token(StaffRole.WAITER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffCart(null, "DINE_IN", "CASH", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TABLE_REQUIRED"));
    }

    @Test
    void staffOnlineOrderUsesTheGuestCheckoutAndIsVerifiedFromTheWaiterScreen() throws Exception {
        String waiter = token(StaffRole.WAITER);
        JsonNode checkout = placeStaffOrder(waiter, "/api/v1/waiter/orders",
                staffCart(table.getId(), "DINE_IN", "ONLINE", UUID.randomUUID().toString()));
        long orderId = checkout.get("orderId").asLong();
        assertThat(checkout.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(checkout.get("mode").asText()).isEqualTo("SDK");
        String rzpOrderId = checkout.get("checkout").get("order_id").asText();

        doReturn(new ProviderPayment(rzpOrderId, "pay_staff1", ProviderPayment.Outcome.CAPTURED, 81900L, "upi", null))
                .when(razorpay).fetchPayment(rzpOrderId, "pay_staff1");
        mvc.perform(post("/api/v1/waiter/payments/verify").header("Authorization", waiter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpay_order_id":"%s","razorpay_payment_id":"pay_staff1","razorpay_signature":"%s"}"""
                                .formatted(rzpOrderId, paymentSignature(rzpOrderId, "pay_staff1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void takeawayIsRejectedWhenDisabledAndRecordedWhenEnabled() throws Exception {
        String guestTakeaway = """
                {"items":[{"itemId":%d,"quantity":1}],"orderType":"TAKEAWAY"}""".formatted(paneer.getId());
        setTakeaway(false);
        try {
            MvcResult rejected = placeOrder(guestCookie(), UUID.randomUUID().toString(), guestTakeaway);
            assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
            assertThat(body(rejected).get("code").asText()).isEqualTo("TAKEAWAY_DISABLED");

            mvc.perform(post("/api/v1/waiter/orders").header("Authorization", token(StaffRole.WAITER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(staffCart(null, "TAKEAWAY", "CASH", UUID.randomUUID().toString())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TAKEAWAY_DISABLED"));
            mvc.perform(get("/api/v1/public/session").param("t", table.getQrToken()))
                    .andExpect(jsonPath("$.restaurant.takeawayEnabled").value(false));
        } finally {
            setTakeaway(true);
        }

        var guest = guestCookie();
        MvcResult accepted = placeOrder(guest, UUID.randomUUID().toString(), guestTakeaway);
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        long orderId = body(accepted).get("orderId").asLong();
        mvc.perform(get("/api/v1/public/orders/" + orderId).cookie(guest))
                .andExpect(jsonPath("$.orderType").value("TAKEAWAY"));
        // Guest orders without a type are dine-in.
        long dineIn = body(placeOrder(guest, UUID.randomUUID().toString(), simpleCart())).get("orderId").asLong();
        assertThat(orderRepository.findById(dineIn).orElseThrow().getOrderType()).isEqualTo(OrderType.DINE_IN);
    }

    // ----- serving -------------------------------------------------------------------------------

    @Test
    void waiterServesReadyOrdersOnly() throws Exception {
        String waiter = token(StaffRole.WAITER);
        String kitchen = token(StaffRole.KITCHEN);
        long orderId = cashOrder(waiter);

        mvc.perform(patch("/api/v1/waiter/orders/" + orderId + "/serve").header("Authorization", waiter))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));

        kitchenStatus(kitchen, orderId, "PREPARING");
        kitchenStatus(kitchen, orderId, "READY");
        JsonNode ready = body(mvc.perform(get("/api/v1/waiter/orders").param("status", "READY")
                .header("Authorization", waiter)).andReturn());
        assertThat(find(ready, orderId)).isNotNull();

        mvc.perform(patch("/api/v1/waiter/orders/" + orderId + "/serve").header("Authorization", waiter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(orderRepository.findById(orderId).orElseThrow().getCompletedAt()).isNotNull();
    }

    // ----- cancellation and counter settlement ---------------------------------------------------

    @Test
    void cancellingAnOfflinePaidOrderMarksAManualRefundWithoutCallingTheGateway() throws Exception {
        long orderId = cashOrder(token(StaffRole.WAITER));
        String manager = token(StaffRole.MANAGER);

        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/cancel").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"guest left\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.manualRefundDue").value(true))
                .andExpect(jsonPath("$.payments[0].provider").value("OFFLINE"))
                .andExpect(jsonPath("$.payments[0].refundStatus").value("MANUAL"));

        verify(razorpay, never()).refund(anyString(), anyString(), anyLong(), anyString());
        assertThat(paymentRepository.findByOrderIdOrderByIdAsc(orderId).getFirst().getRefundStatus())
                .isEqualTo(RefundStatus.MANUAL);
    }

    @Test
    void expiredOrderCanBeSettledInCashAtTheCounter() throws Exception {
        long orderId = body(placeOrder(guestCookie(), UUID.randomUUID().toString(), simpleCart())).get("orderId").asLong();
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setPlacedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        orderRepository.save(order);
        doReturn(List.of()).when(razorpay).fetchOrderPayments(anyString());
        expiryJob.run();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);

        String manager = token(StaffRole.MANAGER);
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/mark-paid-offline").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payments[1].provider").value("OFFLINE"))
                .andExpect(jsonPath("$.payments[1].method").value("CASH"))
                .andExpect(jsonPath("$.payments[1].status").value("CAPTURED"));

        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/mark-paid-offline").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PAID"));
        mvc.perform(post("/api/v1/admin/orders/" + orderId + "/mark-paid-offline").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"ONLINE\"}"))
                .andExpect(status().isBadRequest());
    }

    // ----- reports -------------------------------------------------------------------------------

    @Test
    void reportsBreakDownByPaymentChannelAndOrderType() throws Exception {
        String owner = token(StaffRole.OWNER);
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        JsonNode before = summary(owner, today);

        // Counter takeaway paid by UPI (placed by the owner from admin), waiter dine-in paid in cash, guest online.
        placeStaffOrder(owner, "/api/v1/admin/orders", staffCart(null, "TAKEAWAY", "UPI_AT_COUNTER", UUID.randomUUID().toString()));
        cashOrder(token(StaffRole.WAITER));
        JsonNode checkout = body(placeOrder(guestCookie(), UUID.randomUUID().toString(), simpleCart()));
        String payload = capturedWebhook(checkout.get("checkout").get("order_id").asText(), "pay_rep1", 81900L);
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Razorpay-Signature", webhookSignature(payload))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()))
                .andExpect(status().isOk());

        JsonNode after = summary(owner, today);
        assertThat(after.get("ordersCount").asLong() - before.get("ordersCount").asLong()).isEqualTo(3);
        assertThat(delta(before, after, "paymentChannels", "channel", "ONLINE")).isEqualTo(1);
        assertThat(delta(before, after, "paymentChannels", "channel", "CASH")).isEqualTo(1);
        assertThat(delta(before, after, "paymentChannels", "channel", "UPI_AT_COUNTER")).isEqualTo(1);
        assertThat(delta(before, after, "paymentChannels", "channel", "CARD_AT_COUNTER")).isZero();
        assertThat(delta(before, after, "orderTypes", "orderType", "TAKEAWAY")).isEqualTo(1);
        assertThat(delta(before, after, "orderTypes", "orderType", "DINE_IN")).isEqualTo(2);
        assertThat(after.get("manualRefundAmount")).isNotNull();

        String csv = mvc.perform(get("/api/v1/admin/reports/orders.csv").param("from", today).param("to", today)
                .header("Authorization", owner)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.lines().findFirst().orElseThrow()).contains("order_type").contains("placed_by");
        assertThat(csv).contains("TAKEAWAY").contains("UPI_AT_COUNTER");

        mvc.perform(get("/api/v1/admin/orders").param("orderType", "TAKEAWAY").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderType").value("TAKEAWAY"));
    }

    private JsonNode summary(String owner, String day) throws Exception {
        return body(mvc.perform(get("/api/v1/admin/reports/summary").param("from", day).param("to", day)
                .header("Authorization", owner)).andExpect(status().isOk()).andReturn());
    }

    private static long delta(JsonNode before, JsonNode after, String list, String key, String value) {
        return countOf(after, list, key, value) - countOf(before, list, key, value);
    }

    private static long countOf(JsonNode summary, String list, String key, String value) {
        for (JsonNode row : summary.get(list)) {
            if (value.equals(row.get(key).asText())) {
                return row.get("count").asLong();
            }
        }
        throw new AssertionError(list + " has no " + value + ": " + summary.get(list));
    }

    private static JsonNode find(JsonNode orders, long id) {
        for (JsonNode o : orders) {
            if (o.get("id").asLong() == id) {
                return o;
            }
        }
        return null;
    }

    // ----- table occupancy -------------------------------------------------------------------------

    /** Confirms a guest checkout via the Razorpay webhook, the same way {@code reportsBreakDownByPaymentChannelAndOrderType} does. */
    private void confirmGuestPayment(JsonNode checkout, String paymentId) throws Exception {
        String payload = capturedWebhook(checkout.get("checkout").get("order_id").asText(), paymentId, 81900L);
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Razorpay-Signature", webhookSignature(payload))
                        .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void aFreshScanIsRefusedWhileAGuestOrderIsActiveButTheSeatedGuestIsNotAffected() throws Exception {
        var seated = guestCookie();
        JsonNode checkout = body(placeOrder(seated, UUID.randomUUID().toString(), simpleCart()));
        long orderId = checkout.get("orderId").asLong();
        confirmGuestPayment(checkout, "pay_occ1");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // A different device scanning the same table's QR for the first time is refused.
        mvc.perform(get("/api/v1/public/session").param("t", table.getQrToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_OCCUPIED"));

        // The guest who is already seated (same session cookie) can still re-scan, e.g. to order a second round.
        mvc.perform(get("/api/v1/public/session").param("t", table.getQrToken()).cookie(seated))
                .andExpect(status().isOk());
        long secondRound = body(placeOrder(seated, UUID.randomUUID().toString(), simpleCart())).get("orderId").asLong();
        assertThat(secondRound).isNotEqualTo(orderId);

        // Once the order is no longer active, the table is free for a fresh scan again (the unpaid second round
        // never occupied it in the first place).
        kitchenStatus(token(StaffRole.KITCHEN), orderId, "PREPARING");
        kitchenStatus(token(StaffRole.KITCHEN), orderId, "READY");
        mvc.perform(patch("/api/v1/waiter/orders/" + orderId + "/serve").header("Authorization", token(StaffRole.WAITER)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/session").param("t", table.getQrToken()))
                .andExpect(status().isOk());
    }

    @Test
    void aFreshScanIsAllowedWhileOnlyStaffPlacedOrdersAreActiveOnTheTable() throws Exception {
        // A waiter already at the table placing a cash order doesn't block a guest's own phone from scanning fresh
        // -- staff being present already rules out the "two unattended guest groups collide" case this guards.
        long orderId = cashOrder(token(StaffRole.WAITER));
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        mvc.perform(get("/api/v1/public/session").param("t", table.getQrToken()))
                .andExpect(status().isOk());
    }
}
