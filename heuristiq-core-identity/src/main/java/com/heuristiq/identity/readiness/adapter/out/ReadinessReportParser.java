package com.heuristiq.uam.readiness.adapter.out;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.heuristiq.uam.readiness.domain.ReadinessCheck;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;

/**
 * Reads a service's readiness answer. The report sits under {@code responseObject} (the standard envelope: wallet, reports,
 * DMS, NMS) or under {@code data} (Rider_OnboardingService's envelope); a bare report is accepted too. A body without a
 * {@code checks} array is not a report. A check without an id or with an unknown status is skipped (the profile then shows
 * it as not reported); param values are kept as strings.
 */
public final class ReadinessReportParser {

    private static final Logger log = LoggerFactory.getLogger(ReadinessReportParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReadinessReportParser() {
    }

    /** The checks of the report, or empty when the body is not a readiness report. */
    public static Optional<List<ReadinessCheck>> parse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        JsonNode report = reportNode(root);
        JsonNode checks = report == null ? null : report.get(ReadinessAdapterConstants.FIELD_CHECKS);
        if (checks == null || !checks.isArray()) {
            return Optional.empty();
        }
        List<ReadinessCheck> result = new ArrayList<>();
        for (JsonNode node : checks) {
            check(node).ifPresent(result::add);
        }
        return Optional.of(result);
    }

    private static JsonNode reportNode(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String envelope : List.of(ReadinessAdapterConstants.ENVELOPE_RESPONSE_OBJECT, ReadinessAdapterConstants.ENVELOPE_DATA)) {
            JsonNode inner = root.get(envelope);
            if (inner != null && inner.isObject()) {
                return inner;
            }
        }
        return root;
    }

    private static Optional<ReadinessCheck> check(JsonNode node) {
        String id = text(node, ReadinessAdapterConstants.FIELD_ID);
        Optional<ReadinessStatus> status = ReadinessStatus.parse(text(node, ReadinessAdapterConstants.FIELD_STATUS));
        if (id == null || status.isEmpty()) {
            log.debug("Readiness check skipped: id={} status={}", id, text(node, ReadinessAdapterConstants.FIELD_STATUS));
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = node.get(ReadinessAdapterConstants.FIELD_PARAMS);
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.properties().forEach(entry -> {
                if (!entry.getValue().isNull()) {
                    params.put(entry.getKey(), entry.getValue().isValueNode() ? entry.getValue().asText() : entry.getValue().toString());
                }
            });
        }
        return Optional.of(new ReadinessCheck(id.trim(), status.get(), text(node, ReadinessAdapterConstants.FIELD_MESSAGE_KEY),
                params, text(node, ReadinessAdapterConstants.FIELD_FIX_PATH)));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() || value.asText().isBlank() ? null : value.asText();
    }
}
