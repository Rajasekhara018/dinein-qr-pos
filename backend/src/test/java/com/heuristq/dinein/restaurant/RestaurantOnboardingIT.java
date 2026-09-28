package com.heuristq.dinein.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
