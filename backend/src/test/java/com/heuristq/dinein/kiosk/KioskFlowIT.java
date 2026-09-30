package com.heuristq.dinein.kiosk;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.kiosk.domain.KioskBrandingRepository;
import com.heuristq.dinein.menu.MenuChangedEvent;
import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderSource;
import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kiosk pairing, menu, pay-at-counter ordering, the kill switch, and that a kiosk can never act as staff. */
class KioskFlowIT extends AbstractIntegrationTest {

    @Autowired
    StaffUserRepository staffUserRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    KioskBrandingRepository brandingRepository;
    @Autowired
    ApplicationEventPublisher events;

    /** Branding is one row per restaurant and the test database is shared, so leave no kill switch behind. */
    @AfterEach
    void resetBranding() {
        brandingRepository.deleteAll();
    }

    // ----- helpers -------------------------------------------------------------------------------

    /** The base class seeds items through repositories, which skips the event that clears the menu cache. */
    private void refreshMenuCache() {
        events.publishEvent(new MenuChangedEvent("test seed"));
    }

    /** A staff user whose password is Secret123 and whose PIN is 2468. */
    private StaffUserEntity staffUser(StaffRole role) {
        StaffUserEntity user = new StaffUserEntity();
        user.setUsername("s" + UUID.randomUUID().toString().substring(0, 8));
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode("Secret123"));
        user.setPinHash(passwordEncoder.encode("2468"));
        staffUserRepository.save(user);
        return user;
    }

    private String bearerFor(StaffUserEntity user) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"Secret123\"}".formatted(user.getUsername()))).andReturn();
        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
        return "Bearer " + body(login).get("accessToken").asText();
    }

    private String ownerAuth() throws Exception {
        return bearerFor(staffUser(StaffRole.OWNER));
    }

    private JsonNode createDevice(String owner) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/kiosk-devices").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Entrance kiosk\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return body(result);
    }

    private MvcResult pair(String code) throws Exception {
        return mvc.perform(post("/api/v1/kiosk/pair").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))).andReturn();
    }

    /** Creates a device, pairs it, and returns {@code Bearer <kiosk token>}. */
    private String pairedKiosk(String owner) throws Exception {
        MvcResult paired = pair(createDevice(owner).get("pairingCode").asText());
        assertThat(paired.getResponse().getStatus()).as(paired.getResponse().getContentAsString()).isEqualTo(200);
        return "Bearer " + body(paired).get("deviceToken").asText();
    }

    private String orderBody() {
        return """
                {"orderType":"TAKEAWAY","paymentMode":"PAY_AT_COUNTER","notes":"no onion",
                 "items":[{"itemId":%d,"addonIds":[%d],"quantity":2,"notes":"extra spicy"},
                          {"itemId":%d,"variantId":%d,"quantity":1}]}
                """.formatted(paneer.getId(), paneer.getAddons().getFirst().getId(), biryani.getId(),
                biryani.getVariants().get(1).getId());
    }

    private MvcResult placeKioskOrder(String kiosk, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/kiosk/orders").header("Authorization", kiosk)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    // ----- tests ---------------------------------------------------------------------------------

    @Test
    void pairedKioskLoadsMenuAndBranding() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        refreshMenuCache();

        MvcResult menu = mvc.perform(get("/api/v1/kiosk/menu").header("Authorization", kiosk)).andReturn();
        assertThat(menu.getResponse().getStatus()).isEqualTo(200);
        assertThat(menu.getResponse().getContentAsString()).contains(paneer.getName());

        MvcResult branding = mvc.perform(get("/api/v1/kiosk/branding").header("Authorization", kiosk)).andReturn();
        assertThat(branding.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(branding).get("kioskEnabled").asBoolean()).isTrue();
        assertThat(body(branding).get("paymentModes").get(0).asText()).isEqualTo("PAY_AT_COUNTER");
    }

    @Test
    void kioskOrderIsPendingPaymentAndRepeatingTheKeyReturnsTheSameOrder() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        String key = "kiosk-" + UUID.randomUUID();

        MvcResult first = placeKioskOrder(kiosk, key, orderBody());
        assertThat(first.getResponse().getStatus()).as(first.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode placed = body(first);
        assertThat(placed.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(placed.get("tokenNumber").asInt()).isPositive();
        assertThat(placed.get("total").decimalValue().signum()).isPositive();

        OrderEntity stored = orderRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(OrderSource.KIOSK);
        assertThat(stored.getTableId()).isNull();
        assertThat(stored.getRestaurantId()).isEqualTo(RestaurantEntity.DEFAULT_ID);

        JsonNode retry = body(placeKioskOrder(kiosk, key, orderBody()));
        assertThat(retry.get("orderId").asLong()).isEqualTo(placed.get("orderId").asLong());
        assertThat(retry.get("tokenNumber").asInt()).isEqualTo(placed.get("tokenNumber").asInt());
    }

    @Test
    void counterStaffSettleAKioskOrderAndItReachesTheKitchen() throws Exception {
        String owner = ownerAuth();
        String kiosk = pairedKiosk(owner);
        long orderId = body(placeKioskOrder(kiosk, "kiosk-" + UUID.randomUUID(), orderBody())).get("orderId").asLong();

        mvc.perform(post("/api/v1/admin/orders/{id}/mark-paid-offline", orderId).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}"))
                .andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void unavailableItemIsRejectedWithTheCartProblem() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        paneer.setAvailable(false);
        itemRepository.save(paneer);

        MvcResult result = placeKioskOrder(kiosk, "kiosk-" + UUID.randomUUID(), orderBody());
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("ITEM_UNAVAILABLE");
    }

    @Test
    void onlyPayAtCounterIsAcceptedForNow() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        MvcResult result = placeKioskOrder(kiosk, "kiosk-" + UUID.randomUUID(),
                orderBody().replace("PAY_AT_COUNTER", "UPI"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("UNSUPPORTED_PAYMENT_MODE");
    }

    @Test
    void killSwitchStopsNewKioskOrders() throws Exception {
        String owner = ownerAuth();
        String kiosk = pairedKiosk(owner);

        mvc.perform(put("/api/v1/admin/kiosk-branding").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"kioskEnabled\":false}"))
                .andExpect(status().isOk());

        MvcResult result = placeKioskOrder(kiosk, "kiosk-" + UUID.randomUUID(), orderBody());
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("KIOSK_DISABLED");
    }

    @Test
    void pairingCodeIsSingleUseAndWrongCodesAreRejected() throws Exception {
        String code = createDevice(ownerAuth()).get("pairingCode").asText();

        assertThat(pair(code).getResponse().getStatus()).isEqualTo(200);
        assertThat(pair(code).getResponse().getStatus()).isEqualTo(400);
        assertThat(pair("000000").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void revokedKioskLosesAccess() throws Exception {
        String owner = ownerAuth();
        JsonNode device = createDevice(owner);
        MvcResult paired = pair(device.get("pairingCode").asText());
        String kiosk = "Bearer " + body(paired).get("deviceToken").asText();

        mvc.perform(get("/api/v1/kiosk/menu").header("Authorization", kiosk)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/kiosk-devices/{id}/revoke", device.get("id").asLong())
                .header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/kiosk/menu").header("Authorization", kiosk)).andExpect(status().isUnauthorized());
    }

    @Test
    void kioskTokenCannotReachStaffOrAdminEndpoints() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());

        mvc.perform(get("/api/v1/admin/kiosk-devices").header("Authorization", kiosk))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/kitchen/orders").header("Authorization", kiosk)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/waiter/tables").header("Authorization", kiosk)).andExpect(status().isForbidden());
    }

    @Test
    void kioskEndpointsRequireAKioskToken() throws Exception {
        mvc.perform(get("/api/v1/kiosk/menu")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/kiosk/menu").header("Authorization", ownerAuth())).andExpect(status().isForbidden());
    }

    // ----- counter settlement ----------------------------------------------------------------------

    @Test
    void waiterListsAndSettlesKioskOrdersAtTheCounter() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        String waiter = bearerFor(staffUser(StaffRole.WAITER));
        long orderId = body(placeKioskOrder(kiosk, "kiosk-" + UUID.randomUUID(), orderBody())).get("orderId").asLong();

        MvcResult pending = mvc.perform(get("/api/v1/waiter/kiosk-orders").header("Authorization", waiter)).andReturn();
        assertThat(pending.getResponse().getStatus()).isEqualTo(200);
        JsonNode mine = null;
        for (JsonNode o : body(pending)) {
            if (o.get("id").asLong() == orderId) {
                mine = o;
            }
        }
        assertThat(mine).as("kiosk order listed for the counter").isNotNull();
        assertThat(mine.get("items")).hasSize(2);

        mvc.perform(post("/api/v1/waiter/kiosk-orders/{id}/pay", orderId).header("Authorization", waiter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI_AT_COUNTER\"}"))
                .andExpect(status().isNoContent());
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus().name()).isEqualTo("CONFIRMED");

        JsonNode after = body(mvc.perform(get("/api/v1/waiter/kiosk-orders").header("Authorization", waiter)).andReturn());
        for (JsonNode o : after) {
            assertThat(o.get("id").asLong()).isNotEqualTo(orderId);
        }
        mvc.perform(post("/api/v1/waiter/kiosk-orders/{id}/pay", orderId).header("Authorization", waiter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CASH\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void counterEndpointCannotSettleANonKioskOrder() throws Exception {
        String waiter = bearerFor(staffUser(StaffRole.WAITER));
        MvcResult guestOrder = placeOrder(guestCookie(), "guest-" + UUID.randomUUID(), simpleCart());
        assertThat(guestOrder.getResponse().getStatus()).as(guestOrder.getResponse().getContentAsString()).isEqualTo(200);
        long orderId = body(guestOrder).get("orderId").asLong();

        MvcResult result = mvc.perform(post("/api/v1/waiter/kiosk-orders/{id}/pay", orderId)
                .header("Authorization", waiter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"CASH\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("NOT_A_KIOSK_ORDER");
    }

    // ----- staff unlock ----------------------------------------------------------------------------

    @Test
    void staffCanUnlockTheKioskWithTheirPin() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        StaffUserEntity waiter = staffUser(StaffRole.WAITER);

        MvcResult ok = mvc.perform(post("/api/v1/kiosk/staff-unlock").header("Authorization", kiosk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"pin\":\"2468\"}".formatted(waiter.getUsername()))).andReturn();
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(ok).get("role").asText()).isEqualTo("WAITER");
    }

    @Test
    void wrongPinIsForbiddenNotUnauthorizedSoTheKioskDoesNotUnpairItself() throws Exception {
        String kiosk = pairedKiosk(ownerAuth());
        StaffUserEntity waiter = staffUser(StaffRole.WAITER);

        MvcResult wrong = mvc.perform(post("/api/v1/kiosk/staff-unlock").header("Authorization", kiosk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"pin\":\"9999\"}".formatted(waiter.getUsername()))).andReturn();
        assertThat(wrong.getResponse().getStatus()).isEqualTo(403);

        // Kitchen accounts have a PIN too but are not allowed to open the service menu.
        StaffUserEntity kitchen = staffUser(StaffRole.KITCHEN);
        MvcResult notAllowed = mvc.perform(post("/api/v1/kiosk/staff-unlock").header("Authorization", kiosk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"pin\":\"2468\"}".formatted(kitchen.getUsername()))).andReturn();
        assertThat(notAllowed.getResponse().getStatus()).isEqualTo(403);

        // The kiosk is still paired.
        mvc.perform(get("/api/v1/kiosk/menu").header("Authorization", kiosk)).andExpect(status().isOk());
    }

    // ----- upsell rules ----------------------------------------------------------------------------

    @Test
    void ownerManagesUpsellRulesAndTheKioskReadsThem() throws Exception {
        String owner = ownerAuth();
        String kiosk = pairedKiosk(owner);

        MvcResult created = mvc.perform(post("/api/v1/admin/kiosk-upsells").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"triggerItemId\":%d,\"suggestedItemId\":%d,\"placement\":\"ITEM_ADDED\",\"message\":\"Add a side?\"}"
                        .formatted(biryani.getId(), paneer.getId()))).andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(200);
        long ruleId = body(created).get("id").asLong();

        boolean seen = false;
        for (JsonNode r : body(mvc.perform(get("/api/v1/kiosk/upsells").header("Authorization", kiosk)).andReturn())) {
            seen |= r.get("id").asLong() == ruleId && r.get("suggestedItemId").asLong() == paneer.getId();
        }
        assertThat(seen).isTrue();

        mvc.perform(delete("/api/v1/admin/kiosk-upsells/{id}", ruleId).header("Authorization", owner))
                .andExpect(status().isNoContent());
        for (JsonNode r : body(mvc.perform(get("/api/v1/kiosk/upsells").header("Authorization", kiosk)).andReturn())) {
            assertThat(r.get("id").asLong()).isNotEqualTo(ruleId);
        }
    }

    @Test
    void invalidUpsellRulesAreRejected() throws Exception {
        String owner = ownerAuth();
        // An item cannot suggest itself.
        mvc.perform(post("/api/v1/admin/kiosk-upsells").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"triggerItemId\":%d,\"suggestedItemId\":%d,\"placement\":\"ITEM_ADDED\"}"
                        .formatted(paneer.getId(), paneer.getId()))).andExpect(status().isBadRequest());
        // The suggested item must exist.
        mvc.perform(post("/api/v1/admin/kiosk-upsells").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"suggestedItemId\":999999,\"placement\":\"CHECKOUT\"}"))
                .andExpect(status().isBadRequest());
        // The placement must be one of the two known ones.
        mvc.perform(post("/api/v1/admin/kiosk-upsells").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"suggestedItemId\":%d,\"placement\":\"ANYWHERE\"}".formatted(paneer.getId())))
                .andExpect(status().isBadRequest());
    }
}
