package com.heuristiq.uam.application;

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
 * How the gateway protects the new endpoints, using its own matching (AntPathMatcher, path variables as {@code *}, the most
 * specific pattern wins): the catalogue is covered by the existing APPLICATION_READ entry, and the self-service lookup is in no
 * entry at all (signed-in callers only).
 */
class ApplicationEndpointsPrivilegeTest {
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static JsonNode catalog;

    @BeforeAll
    static void load() throws IOException {
        catalog = new ObjectMapper().readTree(Files.readString(Path.of("src/main/resources/privilege.json")));
    }

    private static List<String> namesMatchingGet(String path) {
        List<String> names = new ArrayList<>();
        String best = null;
        for (JsonNode entry : catalog) {
            String pattern = entry.path("apipath").asText().replaceAll("\\{[^}]+}", "*");
            String operation = entry.path("operation").asText();
            boolean get = operation.equals("READ") || operation.equals("LIST") || operation.equals("MANAGE");
            if (!"uam".equals(entry.path("service").asText()) || !get || !MATCHER.match(pattern, path)) {
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
    void theCatalogueIsProtectedByApplicationRead() {
        assertThat(namesMatchingGet(ApplicationConstants.BASE_PATH + ApplicationConstants.CATALOGUE_PATH)).containsExactly("APPLICATION_READ");
    }

    @Test
    void theLookupIsNotInThePrivilegeCatalogue() {
        assertThat(namesMatchingGet(ApplicationConstants.LOOKUP_PATH)).isEmpty();
    }
}
