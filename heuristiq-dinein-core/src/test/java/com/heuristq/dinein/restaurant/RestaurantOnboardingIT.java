package com.heuristq.dinein.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the multi-tenant work end to end: onboarding creates an isolated restaurant + owner, and from then on
 * neither restaurant's admin panel can see or affect the other's data.
 */
class RestaurantOnboardingIT extends AbstractIntegrationTest {

    // Matches app.security.platform-admin-key in application-test.properties.
    private static final String PLATFORM_KEY = "test_platform_admin_key";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** A minimal, valid CONFIRMED order for the given restaurant, for report-scoping assertions. */
    private void insertConfirmedOrder(long restaurantId, String grandTotal) {
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO orders (order_number, display_token, guest_session_id, status, subtotal, tax_total, "
                        + "grand_total, idempotency_key, placed_at, restaurant_id) "
                        + "VALUES (:orderNumber, 1, :guestSessionId, 'CONFIRMED', :grandTotal, 0, :grandTotal, "
                        + ":idempotencyKey, :placedAt, :restaurantId)",
                new MapSqlParameterSource()
                        .addValue("orderNumber", suffix.substring(0, 20))
                        .addValue("guestSessionId", suffix)
                        .addValue("grandTotal", new java.math.BigDecimal(grandTotal))
                        .addValue("idempotencyKey", suffix)
                        .addValue("placedAt", java.sql.Timestamp.from(Instant.now()))
                        .addValue("restaurantId", restaurantId));
    }

    private JsonNode onboard(String restaurantName) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/platform/restaurants")
                        .header("X-Platform-Admin-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantName\":\"%s\"}".formatted(restaurantName)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return body(result);
    }

    /** Logs in with the temporary password, changes it (forced), and returns a fully privileged access token. */
    private String ownerToken(String username, String temporaryPassword) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, temporaryPassword))).andReturn();
        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(login).get("user").get("mustChangePassword").asBoolean()).isTrue();
        String temporaryToken = body(login).get("accessToken").asText();

        // The temporary token cannot use the admin API yet (see AuthFlowIT.bootstrapOwnerMustChangePasswordBeforeUsingAdminApi).
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isForbidden());

        MvcResult changed = mvc.perform(post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + temporaryToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewSecret456\"}".formatted(temporaryPassword)))
                .andReturn();
        assertThat(changed.getResponse().getStatus()).as(changed.getResponse().getContentAsString()).isEqualTo(200);
        return body(changed).get("accessToken").asText();
    }

    private JsonNode createCategory(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/categories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name))).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return body(result);
    }

    private JsonNode categories(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    private JsonNode staff(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    @Test
    void onboardingRejectsAMissingOrWrongKey() throws Exception {
        mvc.perform(post("/api/v1/platform/restaurants")
                        .header("X-Platform-Admin-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantName\":\"Nope\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/platform/restaurants").header("X-Platform-Admin-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoOnboardedRestaurantsNeverSeeEachOthersData() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode a = onboard("Spice Route " + suffix);
        JsonNode b = onboard("Curry Leaf " + suffix);

        // Different, slug-derived usernames; no collision even though both restaurants used the default owner name.
        assertThat(a.get("ownerUsername").asText()).isNotEqualTo(b.get("ownerUsername").asText());

        String tokenA = ownerToken(a.get("ownerUsername").asText(), a.get("temporaryPassword").asText());
        String tokenB = ownerToken(b.get("ownerUsername").asText(), b.get("temporaryPassword").asText());

        createCategory(tokenA, "A's Starters");
        createCategory(tokenB, "B's Starters");

        JsonNode categoriesA = categories(tokenA);
        assertThat(categoriesA).hasSize(1);
        assertThat(categoriesA.get(0).get("name").asText()).isEqualTo("A's Starters");

        JsonNode categoriesB = categories(tokenB);
        assertThat(categoriesB).hasSize(1);
        assertThat(categoriesB.get(0).get("name").asText()).isEqualTo("B's Starters");

        // Each owner's staff list contains only themself, not the other restaurant's owner.
        JsonNode staffA = staff(tokenA);
        assertThat(staffA).hasSize(1);
        assertThat(staffA.get(0).get("username").asText()).isEqualTo(a.get("ownerUsername").asText());

        JsonNode staffB = staff(tokenB);
        assertThat(staffB).hasSize(1);
        assertThat(staffB.get(0).get("username").asText()).isEqualTo(b.get("ownerUsername").asText());

        // The platform view sees both restaurants, each correctly attributed to its own owner.
        MvcResult list = mvc.perform(get("/api/v1/platform/restaurants").header("X-Platform-Admin-Key", PLATFORM_KEY))
                .andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        JsonNode restaurants = body(list);
        JsonNode entryA = findById(restaurants, a.get("restaurantId").asLong());
        JsonNode entryB = findById(restaurants, b.get("restaurantId").asLong());
        assertThat(entryA.get("owners")).hasSize(1);
        assertThat(entryA.get("owners").get(0).get("username").asText()).isEqualTo(a.get("ownerUsername").asText());
        assertThat(entryB.get("owners")).hasSize(1);
        assertThat(entryB.get("owners").get(0).get("username").asText()).isEqualTo(b.get("ownerUsername").asText());
    }

    private static JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) return node;
        }
        throw new AssertionError("restaurant id " + id + " not found in " + array);
    }

    @Test
    void onboardingAcceptsARestaurantsSettingsAndACustomOwnerLoginUpFront() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "custom-owner-" + suffix;
        MvcResult result = mvc.perform(post("/api/v1/platform/restaurants")
                        .header("X-Platform-Admin-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"restaurantName\":\"Full Details %s\",\"address\":\"12 MG Road\","
                                + "\"phone\":\"9876543210\",\"gstin\":\"29ABCDE1234F1Z5\",\"fssaiNo\":\"12345678901234\","
                                + "\"pricesIncludeGst\":true,\"openingTime\":\"09:00\",\"closingTime\":\"22:00\","
                                + "\"brandColor\":\"#112233\",\"takeawayEnabled\":false,"
                                + "\"ownerUsername\":\"%s\",\"ownerEmail\":\"owner@example.com\",\"ownerPhone\":\"9123456780\","
                                + "\"ownerPassword\":\"CustomPass1\"}").formatted(suffix, username)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode onboarded = body(result);
        assertThat(onboarded.get("ownerUsername").asText()).isEqualTo(username);
        assertThat(onboarded.get("temporaryPassword").asText()).isEqualTo("CustomPass1");

        String token = ownerToken(username, "CustomPass1");
        MvcResult settingsResult = mvc.perform(get("/api/v1/admin/settings").header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(settingsResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode settings = body(settingsResult);
        assertThat(settings.get("address").asText()).isEqualTo("12 MG Road");
        assertThat(settings.get("phone").asText()).isEqualTo("9876543210");
        // Uppercased server-side, matching the settings page's own uppercaseGstin() behaviour.
        assertThat(settings.get("gstin").asText()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(settings.get("fssaiNo").asText()).isEqualTo("12345678901234");
        assertThat(settings.get("pricesIncludeGst").asBoolean()).isTrue();
        assertThat(settings.get("openingTime").asText()).startsWith("09:00");
        assertThat(settings.get("closingTime").asText()).startsWith("22:00");
        assertThat(settings.get("brandColor").asText()).isEqualToIgnoringCase("#112233");
        assertThat(settings.get("takeawayEnabled").asBoolean()).isFalse();
    }

    @Test
    void reportsAndDashboardAreScopedToOneRestaurantExceptForThePlatformAdmin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode a = onboard("Report Scope A " + suffix);
        JsonNode b = onboard("Report Scope B " + suffix);
        String tokenA = ownerToken(a.get("ownerUsername").asText(), a.get("temporaryPassword").asText());
        String tokenB = ownerToken(b.get("ownerUsername").asText(), b.get("temporaryPassword").asText());

        insertConfirmedOrder(a.get("restaurantId").asLong(), "500.00");
        insertConfirmedOrder(b.get("restaurantId").asLong(), "700.00");

        // Each restaurant's own owner sees only its own order in today's dashboard totals.
        JsonNode dashboardA = body(mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andReturn());
        assertThat(dashboardA.get("ordersToday").asLong()).isEqualTo(1);
        assertThat(dashboardA.get("revenueToday").asDouble()).isEqualTo(500.00);

        JsonNode dashboardB = body(mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andReturn());
        assertThat(dashboardB.get("ordersToday").asLong()).isEqualTo(1);
        assertThat(dashboardB.get("revenueToday").asDouble()).isEqualTo(700.00);

        // A platform admin (see BootstrapOwnerRunner -- this flag is what makes an account one) sees both,
        // combined, regardless of which restaurant it happens to belong to. Promoted directly rather than reusing
        // the shared bootstrap "owner" account, which other IT classes in this same suite/container also log
        // into and permanently change the password of -- reusing it here would be order-dependent and flaky.
        JsonNode c = onboard("Report Scope Platform Admin " + suffix);
        String ownerUsername = c.get("ownerUsername").asText();
        jdbc.update("UPDATE staff_user SET platform_admin = true WHERE username = :username",
                new MapSqlParameterSource().addValue("username", ownerUsername));
        String platformAdminToken = ownerToken(ownerUsername, c.get("temporaryPassword").asText());

        JsonNode dashboardPlatform = body(mvc.perform(
                        get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + platformAdminToken))
                .andExpect(status().isOk()).andReturn());
        assertThat(dashboardPlatform.get("ordersToday").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(dashboardPlatform.get("revenueToday").asDouble()).isGreaterThanOrEqualTo(1200.00);
    }

    @Test
    void onboardingRejectsAnOwnerUsernameThatIsAlreadyTaken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "dup-owner-" + suffix;
        mvc.perform(post("/api/v1/platform/restaurants")
                        .header("X-Platform-Admin-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantName\":\"First %s\",\"ownerUsername\":\"%s\"}".formatted(suffix, username)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/platform/restaurants")
                        .header("X-Platform-Admin-Key", PLATFORM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantName\":\"Second %s\",\"ownerUsername\":\"%s\"}".formatted(suffix, username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }
}
