package com.heuristiq.documents.documenttype.application;

import com.heuristiq.documents.common.DmsCommonConstants;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * The canonical text of a JSON value and its SHA-256, in exactly the form Rider_WorkflowService uses for
 * {@code beforeHash} (a copy of {@code com.heuristiq.workflowservice.changerequest.CanonicalJson}; keep the two in
 * step). Form: no whitespace; object keys sorted by UTF-16 code unit; strings as JSON strings; booleans and
 * null as in JSON; numbers by value without trailing zeros ({@code 1}, {@code 1.0}, {@code 1.00} all give
 * {@code 1}; {@code 0.0} and {@code -0} give {@code 0}).
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /** The canonical text of {@code node} (a null node is JSON null). */
    public static String canonical(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString();
    }

    /** Lower-case hex SHA-256 of the UTF-8 bytes of the canonical text. */
    public static String sha256(JsonNode node) {
        try {
            byte[] digest = MessageDigest.getInstance(DmsCommonConstants.SHA_256).digest(canonical(node).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void write(JsonNode node, StringBuilder out) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            out.append("null");
        } else if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(TextNode.valueOf(keys.get(i)));
                out.append(':');
                write(node.get(keys.get(i)), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(node.get(i), out);
            }
            out.append(']');
        } else if (node.isNumber()) {
            BigDecimal value = node.decimalValue();
            out.append(value.signum() == 0 ? "0" : value.stripTrailingZeros().toString());
        } else {
            out.append(node);
        }
    }
}
