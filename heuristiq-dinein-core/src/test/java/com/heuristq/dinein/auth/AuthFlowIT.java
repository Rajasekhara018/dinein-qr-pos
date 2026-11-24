package com.heuristq.dinein.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    StaffUserRepository staffUserRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private String createUser(StaffRole role, boolean mustChange) {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        StaffUserEntity user = new StaffUserEntity();
        user.setUsername(username);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode("Secret123"));
        user.setPinHash(passwordEncoder.encode("4321"));
        user.setMustChangePassword(mustChange);
        staffUserRepository.save(user);
        return username;
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))).andReturn();
    }

    @Test
    void bootstrapOwnerMustChangePasswordBeforeUsingAdminApi() throws Exception {
        MvcResult result = login("owner", "Owner@12345");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.get("user").get("mustChangePassword").asBoolean()).isTrue();
        String token = body.get("accessToken").asText();

        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void forcedPasswordChangeUnlocksAdminApi() throws Exception {
        String username = createUser(StaffRole.MANAGER, true);
        String token = body(login(username, "Secret123")).get("accessToken").asText();

        MvcResult changed = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Secret123\",\"newPassword\":\"NewSecret456\"}")).andReturn();
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        String fresh = body(changed).get("accessToken").asText();

        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void locksAccountAfterFiveFailedAttempts() throws Exception {
        String username = createUser(StaffRole.MANAGER, false);
        for (int i = 0; i < 5; i++) {
            assertThat(login(username, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Secret123\"}".formatted(username)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesTheFamily() throws Exception {
        String username = createUser(StaffRole.MANAGER, false);
        Cookie first = login(username, "Secret123").getResponse().getCookie("dinein_rt");
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(first)).andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        Cookie second = refreshed.getResponse().getCookie("dinein_rt");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated token is treated as theft: it fails and kills the newer token too.
        mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresCsrfToken() throws Exception {
        String username = createUser(StaffRole.MANAGER, false);
        Cookie rt = login(username, "Secret123").getResponse().getCookie("dinein_rt");

        mvc.perform(post("/api/v1/auth/refresh").cookie(rt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void managerCannotReachOwnerOnlyEndpoints() throws Exception {
        String token = body(login(createUser(StaffRole.MANAGER, false), "Secret123")).get("accessToken").asText();

        mvc.perform(get("/api/v1/admin/staff").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports/summary?from=2026-09-01&to=2026-09-26")
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/tables").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void waiterSignsInWithPasswordOrPinAndReachesOnlyTheWaiterApi() throws Exception {
        String username = createUser(StaffRole.WAITER, false);

        MvcResult byPin = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"pin\":\"4321\"}".formatted(username))).andReturn();
        assertThat(byPin.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(byPin).get("user").get("role").asText()).isEqualTo("WAITER");
        assertThat(byPin.getResponse().getCookie("dinein_rt")).isNotNull();
        String token = body(byPin).get("accessToken").asText();

        mvc.perform(get("/api/v1/waiter/tables").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/kitchen/orders").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        assertThat(login(username, "Secret123").getResponse().getStatus()).isEqualTo(200);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"pin\":\"9999\"}".formatted(username)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\"}".formatted(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_REQUIRED"));
    }

    @Test
    void pinSignInIsOnlyForWaiters() throws Exception {
        String manager = createUser(StaffRole.MANAGER, false);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"pin\":\"4321\"}".formatted(manager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_LOGIN_NOT_ALLOWED"));
    }

    @Test
    void ownerCreatesWaiterAccounts() throws Exception {
        String owner = body(login(createUser(StaffRole.OWNER, false), "Secret123")).get("accessToken").asText();
        String username = "waiter" + UUID.randomUUID().toString().substring(0, 6);

        mvc.perform(post("/api/v1/admin/staff").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"Ravi","role":"WAITER","password":"Waiter@123","pin":"1357"}"""
                                .formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.hasPin").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"pin\":\"1357\"}".formatted(username)))
                .andExpect(status().isOk());
    }

    @Test
    void kitchenDeviceTokenViaPinReachesKitchenButNotAdmin() throws Exception {
        String username = createUser(StaffRole.KITCHEN, false);
        MvcResult device = mvc.perform(post("/api/v1/auth/kitchen-device").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"pin\":\"4321\",\"deviceName\":\"Line tablet\"}".formatted(username))).andReturn();
        assertThat(device.getResponse().getStatus()).isEqualTo(200);
        String token = body(device).get("deviceToken").asText();
        assertThat(token).startsWith("dvc_");

        mvc.perform(get("/api/v1/kitchen/orders").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/categories").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        assertThat(login(username, "Secret123").getResponse().getStatus()).isEqualTo(403);
    }
}
