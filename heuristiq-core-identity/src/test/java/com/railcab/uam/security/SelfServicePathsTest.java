package com.heuristiq.uam.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.device.UserDeviceProfileController;
import com.heuristiq.uam.lockout.UserLoginSecurityProfileController;
import com.heuristiq.uam.mfa.UserMfaProfileController;
import com.heuristiq.uam.session.UserSessionProfileController;
import com.heuristiq.uam.userpreference.UserPreferenceProfileController;
import com.heuristiq.uam.users.UserProfileController;

/**
 * A person's own profile, preferences, sessions, devices, MFA and login security must work for everyone who is signed in, whatever
 * roles they hold. They are therefore also served under /api/v1/uam/identity/me/**, a path the privilege catalogue does not cover
 * (authenticated only); under /users/** the wildcard UAM_USERS_MANAGE entry made an agent or owner fail with 403.
 */
class SelfServicePathsTest {

    private static final String IDENTITY_ME = "/api/v1/uam/identity/me/";

    private static final List<Class<?>> SELF_SERVICE = List.of(UserProfileController.class, UserDeviceProfileController.class,
            UserLoginSecurityProfileController.class, UserMfaProfileController.class, UserSessionProfileController.class,
            UserPreferenceProfileController.class);

    private static List<String> identityPathsOf(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        List<String> paths = new ArrayList<>();
        for (String path : mapping.value()) {
            if (path.startsWith(IDENTITY_ME)) {
                paths.add(path);
            }
        }
        return paths;
    }

    @Test
    void everySelfServiceControllerAlsoLivesUnderTheIdentityPath() {
        for (Class<?> controller : SELF_SERVICE) {
            assertThat(identityPathsOf(controller)).as(controller.getSimpleName()).hasSize(1);
        }
    }

    @Test
    void noCatalogueEntryCoversTheIdentityPaths() throws Exception {
        JsonNode catalogue;
        try (InputStream in = getClass().getResourceAsStream("/privilege.json")) {
            catalogue = new ObjectMapper().readTree(in);
        }
        JsonNode entries = catalogue.isArray() ? catalogue : catalogue.get("privileges");
        AntPathMatcher matcher = new AntPathMatcher();
        for (Class<?> controller : SELF_SERVICE) {
            String path = identityPathsOf(controller).get(0);
            for (JsonNode entry : entries) {
                if (!"uam".equals(entry.path("service").asText())) {
                    continue; // the gateway only matches a path against the entries of the service that owns it
                }
                assertThat(matcher.match(entry.get("apipath").asText().replaceAll("\\{[^/]+}", "*"), path + "/x"))
                        .as("%s is covered by %s", path, entry.get("name").asText()).isFalse();
                assertThat(matcher.match(entry.get("apipath").asText().replaceAll("\\{[^/]+}", "*"), path))
                        .as("%s is covered by %s", path, entry.get("name").asText()).isFalse();
            }
        }
    }
}
