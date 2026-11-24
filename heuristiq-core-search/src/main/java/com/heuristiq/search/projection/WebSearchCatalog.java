package com.heuristiq.search.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Source-owned model contracts shared with the web clients and gateway. */
public final class WebSearchCatalog {
    private static final Map<String,JsonNode> MODELS = load();
    private WebSearchCatalog() {}
    private static Map<String,JsonNode> load() {
        try (var input = WebSearchCatalog.class.getResourceAsStream("/web-search-models.json")) {
            if (input == null) throw new IllegalStateException("Missing web search model catalog");
            Map<String,JsonNode> result = new LinkedHashMap<>();
            for (JsonNode row : new ObjectMapper().readTree(input)) {
                String model = normalize(row.path("model").asText());
                if (!model.startsWith("WEB_") || result.put(model, row) != null) throw new IllegalStateException("Invalid or duplicate web search model");
            }
            return Map.copyOf(result);
        } catch (IOException e) { throw new IllegalStateException("Cannot read web search model contracts", e); }
    }
    public static boolean contains(String model) { return model != null && MODELS.containsKey(normalize(model)); }
    public static JsonNode require(String model) {
        JsonNode spec = MODELS.get(normalize(model));
        if (spec == null) throw new IllegalArgumentException("Unknown web search model: " + model);
        return spec;
    }
    public static java.util.Collection<JsonNode> all() { return MODELS.values(); }
    private static String normalize(String model) { return model.toUpperCase(Locale.ROOT).replace('-', '_'); }
}
