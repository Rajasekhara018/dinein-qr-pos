package com.heuristiq.documents.search.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.heuristiq.documents.common.paging.PagedResult;
import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import com.heuristiq.documents.documenttype.application.DocumentTypeQueryService;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.lifecycle.application.LifecycleQueryService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the two sources publish to Rider_SearchAPI's catalogue ({@code web-search-models.json}, read from the
 * monorepo): every slot it names, the normalizers, the filter slots, the order slot and the id. If the catalogue
 * changes and a source does not follow, this fails.
 *
 * <p>The catalogue sits beside this module in the monorepo; a checkout without it skips these tests.
 */
class ProjectionCatalogContractTest {

    private static final Path CATALOGUE = Path.of("..", "Rider_SearchAPI", "src", "main", "resources", "web-search-models.json");
    private static final Pattern SLOT = Pattern.compile("^(text|amount|number|date|datetime)\\d+$");

    private static JsonNode spec(String model) throws IOException {
        assumeTrue(Files.exists(CATALOGUE), "Rider_SearchAPI's catalogue is not beside this module: " + CATALOGUE.toAbsolutePath());
        for (JsonNode row : ProjectionSourcesTest.MAPPER.readTree(CATALOGUE.toFile())) {
            if (model.equals(row.path("model").asText())) {
                return row;
            }
        }
        throw new AssertionError("The catalogue has no model " + model);
    }

    private ProjectionRecord documentTypeRecord() {
        DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
        when(query.list("org-a", null, null, null, 0, 100)).thenReturn(new DocumentTypeRepository.Page(
                List.of(ProjectionSourcesTest.type("t-1", "org-a", "PHOTO_ID", "Customer-Documents", "onboarding", true)), 0, 100, 1, 1));
        return new DocumentTypeProjectionSource(query, ProjectionSourcesTest.MAPPER).page("org-a", 0, 100).records().get(0);
    }

    private ProjectionRecord lifecycleRecord() {
        LifecycleQueryService query = mock(LifecycleQueryService.class);
        when(query.searchDocuments(eq("org-a"), any(DocumentSearchCriteria.class), eq(0), eq(100)))
                .thenReturn(new PagedResult<>(List.of(ProjectionSourcesTest.document("d-1", "org-a")), 0, 100, 1));
        return new LifecycleProjectionSource(query, ProjectionSourcesTest.MAPPER).page("org-a", 0, 100).records().get(0);
    }

    private static Set<String> slotsOf(Map<String, Object> fields) {
        Set<String> slots = new HashSet<>();
        fields.keySet().stream().filter(key -> SLOT.matcher(key).matches()).forEach(slots::add);
        return slots;
    }

    /** What the catalogue says the source must fill: its slot map, plus any slot only a filter reads (text13). */
    private static Set<String> expectedSlots(JsonNode spec) {
        Set<String> expected = new HashSet<>();
        spec.path("slots").fieldNames().forEachRemaining(expected::add);
        spec.path("filters").forEach(binding -> {
            if (SLOT.matcher(binding.get(0).asText()).matches()) {
                expected.add(binding.get(0).asText());
            }
        });
        return expected;
    }

    private void assertMatchesCatalogue(String model, ProjectionRecord record) throws IOException {
        JsonNode spec = spec(model);
        Map<String, Object> fields = record.fields();
        Map<?, ?> payload = (Map<?, ?>) fields.get("payload");

        assertEquals("TENANT", spec.path("scope").asText(), "the sources publish per organization");
        assertEquals(payload.get(spec.path("id").asText()), record.modelKey(), "the model key is the catalogue's id field");
        assertEquals(payload.get(spec.path("createdAt").asText()), fields.get("createdAt"), "createdAt comes from the catalogue's field");

        Set<String> expected = expectedSlots(spec);
        assertEquals(expected, slotsOf(fields), model + ": the slots the source fills must be exactly the catalogue's");

        spec.path("slots").fields().forEachRemaining(entry -> {
            Object source = payload.get(entry.getValue().asText());
            String normalizer = spec.path("normalizers").path(entry.getKey()).asText("");
            String want = String.valueOf(source);
            if ("lower".equals(normalizer)) {
                want = want.toLowerCase(Locale.ROOT);
            } else if ("upper".equals(normalizer)) {
                want = want.toUpperCase(Locale.ROOT);
            }
            assertEquals(want, String.valueOf(fields.get(entry.getKey())),
                    model + ": " + entry.getKey() + " must hold " + entry.getValue().asText() + " (" + (normalizer.isEmpty() ? "as is" : normalizer) + ")");
        });

        if (spec.path("sourceOrdered").asBoolean()) {
            assertEquals(ProjectionConstants.SLOT_POSITION, spec.path("sort").path("field1").asText(),
                    model + ": the catalogue sorts by the slot the publisher numbers");
            assertEquals("ASC", spec.path("sort").path("directionField1").asText());
        }
        assertTrue(fields.get("payload") instanceof Map, "the payload is the public DTO");
    }

    @Test
    void documentTypeRecordsMatchTheCatalogue() throws IOException {
        assertMatchesCatalogue(ProjectionConstants.MODEL_DOCUMENT_TYPE, documentTypeRecord());
    }

    @Test
    void lifecycleRecordsMatchTheCatalogue() throws IOException {
        assertMatchesCatalogue(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT, lifecycleRecord());
    }

    @Test
    void theFreeTextSlotIsLowerCaseBecauseTheWebClientLowerCasesTheQuery() throws IOException {
        JsonNode spec = spec(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT);
        assertEquals(ProjectionConstants.SLOT_QUERY_TEXT, spec.path("filters").path("query").get(0).asText());
        String text = (String) lifecycleRecord().fields().get(ProjectionConstants.SLOT_QUERY_TEXT);
        assertEquals(text.toLowerCase(Locale.ROOT), text);
    }
}
