package com.heuristiq.uam.organization.operatingmodel.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * How the gateway protects the operating-model endpoints, with its own matching (PrivilegeBasedAccessFilter: AntPathMatcher,
 * path variables as {@code *}, method from the operation, only the entries at the most specific pattern decide). The path
 * must be decided by its own READ / UPDATE entries, never by ORGANIZATION_READ / _UPDATE of {@code /organizations/{id}},
 * and a View-only role must never be able to change it.
 */
class OperatingModelEndpointsPrivilegeTest {
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String PATH = "/api/v1/uam/organizations/acme/operating-model";
    private static JsonNode catalog;

    @BeforeAll
    static void load() throws IOException {
        catalog = new ObjectMapper().readTree(Files.readString(Path.of("src/main/resources/privilege.json")));
    }

    private static List<String> decidingNames(String method, String path) {
        List<String> names = new ArrayList<>();
        String best = null;
        for (JsonNode entry : catalog) {
            String pattern = entry.path("apipath").asText().replaceAll("\\{[^/]+}", "*");
            boolean methodMatches = switch (entry.path("operation").asText()) {
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

    @Test
    void readingIsDecidedOnlyByTheReadPrivilege() {
        assertThat(decidingNames("GET", PATH)).containsExactly("ORGANIZATION_OPERATING_MODEL_READ");
    }

    @Test
    void schedulingIsDecidedOnlyByTheUpdatePrivilege() {
        assertThat(decidingNames("PUT", PATH)).containsExactly("ORGANIZATION_OPERATING_MODEL_UPDATE");
    }

    @Test
    void nothingProtectsAMethodTheEndpointDoesNotServe() {
        assertThat(decidingNames("POST", PATH)).isEmpty();
        assertThat(decidingNames("DELETE", PATH)).isEmpty();
    }

    @Test
    void theInternalLookupIsNotInTheCatalogue() {
        assertThat(decidingNames("GET", "/api/v1/uam/internal/organizations/acme/operating-model")).isEmpty();
    }
}
