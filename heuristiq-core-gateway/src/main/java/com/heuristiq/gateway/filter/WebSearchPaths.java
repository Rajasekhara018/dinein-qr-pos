package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Each web model inherits the source list's existing live permission, including its service slug. */
final class WebSearchPaths {
    record Source(String path, String service, boolean authenticatedRead, java.util.List<String> applicationCodes) {}
    private static final Map<String,Source> SOURCES = load();
    private WebSearchPaths() {}
    private static Map<String,Source> load() {
        try (var input = WebSearchPaths.class.getResourceAsStream("/web-search-models.json")) {
            if (input == null) throw new IllegalStateException("Missing web search model contracts");
            Map<String,Source> result = new LinkedHashMap<>();
            for (var row : new ObjectMapper().readTree(input)) {
                var apps = new java.util.ArrayList<String>();
                row.path("applicationCodes").forEach(code -> apps.add(code.asText()));
                result.put(normalize(row.path("model").asText()),
                    new Source(row.path("path").asText().replaceAll("\\{[^}]+}", "scope"), row.path("service").asText(), row.path("authenticatedRead").asBoolean(false), java.util.List.copyOf(apps)));
            }
            return Map.copyOf(result);
        } catch (IOException e) { throw new IllegalStateException("Cannot load web search authorization", e); }
    }
    static Source source(String path, HttpMethod method) {
        String prefix = "/api/v1/search/elastic/", suffix = "/getall";
        if (method != HttpMethod.POST || !path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String model = path.substring(prefix.length(), path.length() - suffix.length());
        Source source = SOURCES.get(normalize(model));
        if (source == null && normalize(model).startsWith("WEB")) return new Source("/__unknown_web_search_model__", "__denied__", false, java.util.List.of());
        return source;
    }
    private static String normalize(String value) { return value.replaceAll("[-_\\s]", "").toUpperCase(Locale.ROOT); }
}
