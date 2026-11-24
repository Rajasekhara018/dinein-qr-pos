package com.heuristiq.uam.readiness.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * How the gateway protects the readiness endpoints, with its own matching (PrivilegeBasedAccessFilter: AntPathMatcher, path
 * variables as {@code *}, method from the operation, only the entries at the most specific pattern decide). The summary path
 * {@code /api/v1/uam/applications/readiness} also matches APPLICATION_READ's {@code /api/v1/uam/applications/{id}}; the
 * literal pattern is more specific, so only APPLICATION_READINESS_LIST decides it.
 */
class ReadinessEndpointsPrivilegeTest {
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static JsonNode catalog;

    @BeforeAll
    static void load() throws IOException {
        catalog = new ObjectMapper().readTree(Files.readString(Path.of("src/main/resources/privilege.json")));
    }

    /** The names of the entries that decide a request, as the gateway picks them. */
    private static List<String> decidingNames(String method, String path) {
        List<String> names = new ArrayList<>();
        String best = null;
        for (JsonNode entry : catalog) {
            String pattern = entry.path("apipath").asText().replaceAll("\\{[^/]+}", "*");
            String operation = entry.path("operation").asText();
            boolean methodMatches = switch (operation) {
                case "READ", "LIST" -> method.equals("GET");
                case "CREATE" -> method.equals("POST");
                case "UPDATE" -> method.equals("PUT") || method.equals("PATCH");
                case "DELETE" -> method.equals("DELETE");
                default -> true;
            };
            if (!"uam".equals(entry.path("service").asText()) || !methodMatches || !MATCHER.match(pattern, path)) {
                continue;
            }
            int order = best == null ? -1 : MATCHER.getPatternComparator(path).compare(pattern, best);
            if (order < 0) {
                best = pattern;
                names.clear();
            }
            if (order <= 0) {
                names.add(entry.path("name").asText());
            }
        }
        return names;
    }

    private static JsonNode entry(String name) {
        for (JsonNode entry : catalog) {
            if (name.equals(entry.path("name").asText())) {
                return entry;
            }
        }
        throw new AssertionError("missing " + name);
    }

    @Test
    void theSummaryIsDecidedOnlyByTheNewListPrivilege() {
        assertThat(decidingNames("GET", "/api/v1/uam/applications/readiness")).containsExactly("APPLICATION_READINESS_LIST");
    }

    @Test
    void oneApplicationsReadinessIsDecidedOnlyByTheNewReadPrivilege() {
        assertThat(decidingNames("GET", "/api/v1/uam/applications/BEST_PURCHASE_APP/readiness")).containsExactly("APPLICATION_READINESS_READ");
    }

    @Test
    void theExistingApplicationEndpointsKeepTheirPrivileges() {
        assertThat(decidingNames("GET", "/api/v1/uam/applications/app-id-1")).containsExactly("APPLICATION_READ");
        assertThat(decidingNames("GET", "/api/v1/uam/applications/catalogue")).containsExactly("APPLICATION_READ");
        assertThat(decidingNames("GET", "/api/v1/uam/applications")).containsExactly("APPLICATION_LIST");
        assertThat(decidingNames("PUT", "/api/v1/uam/applications/app-id-1")).containsExactly("APPLICATION_UPDATE");
    }

    @Test
    void theEntriesFollowTheCatalogueConventions() {
        JsonNode read = entry("APPLICATION_READINESS_READ");
        JsonNode list = entry("APPLICATION_READINESS_LIST");
        assertThat(read.path("operation").asText()).isEqualTo("READ");
        assertThat(list.path("operation").asText()).isEqualTo("LIST");
        assertThat(read.path("apipath").asText()).isEqualTo(ReadinessWebConstants.BASE_PATH + ReadinessWebConstants.APPLICATION_PATH);
        assertThat(list.path("apipath").asText()).isEqualTo(ReadinessWebConstants.BASE_PATH + ReadinessWebConstants.SUMMARY_PATH);
        assertThat(read.path("service").asText()).isEqualTo("uam");
        assertThat(list.path("service").asText()).isEqualTo("uam");
        assertThat(read.path("description").asText()).startsWith("Allows ").endsWith(".");
        assertThat(list.path("description").asText()).startsWith("Allows ").endsWith(".");
        assertThat(read.path("privilegeId").asInt()).isEqualTo(1073);
        assertThat(list.path("privilegeId").asInt()).isEqualTo(1074);
    }

    @Test
    void theSeverityEndpointsAreDecidedOnlyByTheirOwnPrivileges() {
        assertThat(decidingNames("PUT", "/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity"))
                .containsExactly("APPLICATION_READINESS_SEVERITY_UPDATE");
        assertThat(decidingNames("DELETE", "/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity/WALLET_APP_ENABLED"))
                .containsExactly("APPLICATION_READINESS_SEVERITY_DELETE");
        assertThat(decidingNames("DELETE", "/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity"))
                .containsExactly("APPLICATION_READINESS_SEVERITY_ALL_DELETE");
        // The shorter application entries are not shadowing them and are not widened by them.
        assertThat(decidingNames("DELETE", "/api/v1/uam/applications/app-id-1")).containsExactly("APPLICATION_DELETE");
        assertThat(decidingNames("PUT", "/api/v1/uam/applications/app-id-1")).containsExactly("APPLICATION_UPDATE");
        // A GET on the severity path has no entry at all (there is no such endpoint).
        assertThat(decidingNames("GET", "/api/v1/uam/applications/BEST_PURCHASE_APP/readiness-severity")).isEmpty();
    }

    @Test
    void theSeverityEntriesFollowTheCatalogueConventions() {
        JsonNode update = entry("APPLICATION_READINESS_SEVERITY_UPDATE");
        JsonNode delete = entry("APPLICATION_READINESS_SEVERITY_DELETE");
        JsonNode deleteAll = entry("APPLICATION_READINESS_SEVERITY_ALL_DELETE");
        assertThat(update.path("operation").asText()).isEqualTo("UPDATE");
        assertThat(delete.path("operation").asText()).isEqualTo("DELETE");
        assertThat(deleteAll.path("operation").asText()).isEqualTo("DELETE");
        assertThat(update.path("apipath").asText()).isEqualTo(ReadinessWebConstants.BASE_PATH + ReadinessWebConstants.SEVERITY_PATH);
        assertThat(delete.path("apipath").asText()).isEqualTo(ReadinessWebConstants.BASE_PATH + ReadinessWebConstants.SEVERITY_STEP_PATH);
        assertThat(deleteAll.path("apipath").asText()).isEqualTo(ReadinessWebConstants.BASE_PATH + ReadinessWebConstants.SEVERITY_PATH);
        for (JsonNode node : List.of(update, delete, deleteAll)) {
            assertThat(node.path("service").asText()).isEqualTo("uam");
            assertThat(node.path("description").asText()).startsWith("Allows ").endsWith(".");
        }
        assertThat(update.path("privilegeId").asInt()).isEqualTo(1079);
        assertThat(delete.path("privilegeId").asInt()).isEqualTo(1080);
        assertThat(deleteAll.path("privilegeId").asInt()).isEqualTo(1081);
    }

    @Test
    void theGatewayCopyIsByteIdentical() throws IOException {
        assertThat(Files.readAllBytes(Path.of("../Rider_ApiGateway/src/main/resources/privilege.json")))
                .isEqualTo(Files.readAllBytes(Path.of("src/main/resources/privilege.json")));
    }
}
