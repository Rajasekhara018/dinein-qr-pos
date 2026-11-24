package com.railcab.nms.search.weblist;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import org.springframework.http.HttpEntity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the rows of one of this service's own list responses (the common envelope around a Spring page or a plain list),
 * page after page, without guessing past a failed response or silently stopping early.
 */
final class WebListRows {

    private static final String MISSING = "__missing_source_field__";

    private WebListRows() {
    }

    /**
     * @param read     returns the response of page {@code n} (a {@code ResponseEntity}, an envelope, a page or a list)
     * @param paged    whether to keep reading pages until the last one
     * @param rowsPath where the rows are inside the envelope's {@code responseObject} ({@code content} for a page, blank for a list)
     * @param sink     receives each row as a map
     */
    static void scan(ObjectMapper mapper, IntFunction<?> read, boolean paged, String rowsPath, Consumer<Map<String, Object>> sink) {
        for (int page = 0; ; page++) {
            Object response = read.apply(page);
            if (response instanceof HttpEntity<?> entity) {
                response = entity.getBody();
            }
            JsonNode root = mapper.valueToTree(response);
            if (root == null || root.isNull()) {
                throw new IllegalStateException("The list returned nothing");
            }
            if (root.has("success") && !root.path("success").asBoolean()) {
                throw new IllegalStateException("The list answered with a failure: " + root.path("message").asText(""));
            }
            JsonNode data = root.has("responseObject") ? root.get("responseObject") : root;
            if (data == null || data.isNull()) {
                throw new IllegalStateException("The list response has no responseObject");
            }
            JsonNode rows = at(data, rowsPath);
            if (!rows.isArray()) {
                throw new IllegalStateException("The list response has no rows at '" + rowsPath + "'");
            }
            for (JsonNode row : rows) {
                if (!row.isObject()) {
                    throw new IllegalStateException("A list row must be an object");
                }
                sink.accept(mapper.convertValue(row, new TypeReference<Map<String, Object>>() { }));
            }
            if (!paged || rows.isEmpty()) {
                return;
            }
            JsonNode totalPages = data.path("totalPages");
            if (totalPages.isMissingNode()) {
                totalPages = data.path("page").path("totalPages");
            }
            if ((totalPages.isIntegralNumber() && page + 1 >= totalPages.asLong()) || data.path("last").asBoolean(false)) {
                return;
            }
        }
    }

    /** The value at a dotted path, trying each {@code |}-separated alternative in turn; a missing node when none has one. */
    static JsonNode at(JsonNode node, String paths) {
        if (paths == null || paths.isBlank()) {
            return node;
        }
        for (String path : paths.split("\\|")) {
            JsonNode value = node;
            for (String field : path.split("\\.")) {
                value = value.path(field);
            }
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return node.path(MISSING);
    }
}
