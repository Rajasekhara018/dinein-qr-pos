package com.heuristiq.identity.pos.adapter.in;

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
 * How the gateway protects the POS organization endpoints, with its own matching (PrivilegeBasedAccessFilter: AntPathMatcher,
 * path variables as {@code *}, method from the operation, only the entries at the most specific pattern decide).
 */
class PosEndpointsPrivilegeTest {
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final String ORG = "/api/v1/uam/organizations/acme";
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

    private static final String[][] ROWS = {
        {"GET", "/legal-entities", "POS_LEGAL_ENTITY_LIST"},
        {"GET", "/legal-entities/le1", "POS_LEGAL_ENTITY_READ"},
        {"POST", "/legal-entities", "POS_LEGAL_ENTITY_CREATE"},
        {"PUT", "/legal-entities/le1", "POS_LEGAL_ENTITY_UPDATE"},
        {"GET", "/branches", "POS_BRANCH_LIST"},
        {"GET", "/branches/b1", "POS_BRANCH_READ"},
        {"POST", "/branches", "POS_BRANCH_CREATE"},
        {"PUT", "/branches/b1", "POS_BRANCH_UPDATE"},
        {"GET", "/branches/b1/staff", "POS_BRANCH_STAFF_READ"},
        {"PUT", "/branches/b1/staff", "POS_BRANCH_STAFF_UPDATE"},
    };

    @Test
    void everyEndpointIsDecidedByExactlyItsOwnPrivilege() {
        for (String[] row : ROWS) {
            assertThat(decidingNames(row[0], ORG + row[1])).as(row[0] + " " + row[1]).containsExactly(row[2]);
        }
    }

    @Test
    void staffReadIsNeverDecidedByBranchRead() {
        assertThat(decidingNames("GET", ORG + "/branches/b1/staff")).containsExactly("POS_BRANCH_STAFF_READ")
                .doesNotContain("POS_BRANCH_READ");
    }

    @Test
    void aReadPrivilegeNeverDecidesAChange() {
        for (String[] row : ROWS) {
            if (!row[0].equals("GET")) {
                assertThat(decidingNames(row[0], ORG + row[1])).as(row[0] + " " + row[1])
                        .noneMatch(n -> n.endsWith("_READ") || n.endsWith("_LIST"));
            }
        }
    }

    @Test
    void noExistingEntryDecidesANewPath() {
        for (String[] row : ROWS) {
            assertThat(decidingNames(row[0], ORG + row[1])).as(row[0] + " " + row[1]).allMatch(n -> n.startsWith("POS_"));
        }
    }

    @Test
    void identityAndGatewayCatalogsAreIdentical() throws IOException {
        assertThat(Files.readAllBytes(Path.of("../heuristiq-core-gateway/src/main/resources/privilege.json")))
                .isEqualTo(Files.readAllBytes(Path.of("src/main/resources/privilege.json")));
    }
}
