package com.heuristiq.audit.application;

import static com.heuristiq.audit.application.RedactionConstants.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes secrets from HTTP headers and bodies before they are stored. The same rules run in the API gateway
 * (before an event leaves it) and again here at ingest (defence in depth); keep the two copies in step.
 *
 * <p>Redaction keeps the key names and replaces the value with {@code ***}; card and account numbers keep their
 * last four digits, phone and email use the pattern of {@link EventSanitizer#maskRecipient}, UPI addresses keep
 * two characters and the handle. Nested objects and arrays are walked recursively and keys match
 * case-insensitively ignoring {@code _ - .}. Bodies that are not valid JSON (for example cut at the size cap)
 * are redacted with a text scan instead.
 */
public final class PayloadRedactor {

    private PayloadRedactor() {
    }

    /**
     * Redacts header values that carry credentials: Authorization, Cookie, Set-Cookie, the internal service,
     * gateway and NMS tokens and any header whose name contains token, secret, key or password. Names are kept.
     *
     * @param headers raw headers (may be null)
     * @param max     maximum number of headers kept
     * @return a new map with redacted values, or null when there are none
     */
    public static Map<String, String> redactHeaders(Map<String, String> headers, int max) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (out.size() >= max) {
                break;
            }
            String name = clip(e.getKey(), MAX_HEADER_NAME);
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = e.getValue() == null ? "" : e.getValue();
            out.put(name, isSensitiveHeader(name) ? MASK : scrubValue(clip(value, MAX_HEADER_VALUE)));
        }
        return out.isEmpty() ? null : out;
    }

    /** @return true when the header name must never be stored in clear */
    public static boolean isSensitiveHeader(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("authorization") || n.equals("proxy-authorization") || n.contains("cookie")
                || n.contains("token") || n.contains("secret") || n.contains("key") || n.contains("password")
                || n.contains("signature");
    }

    /**
     * Redacts a body.
     *
     * @param body        the (possibly truncated) body text
     * @param contentType its content type, used as a hint; JSON and form bodies are also recognised by shape
     * @return the redacted text
     */
    public static String redactBody(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String trimmed = body.stripLeading();
        boolean jsonShape = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (type.contains("x-www-form-urlencoded") || (!jsonShape && type.isEmpty() && looksLikeForm(trimmed))) {
            return redactForm(body);
        }
        if (type.contains("json") || jsonShape) {
            try {
                JsonNode root = MAPPER.readTree(body);
                if (root != null && (root.isObject() || root.isArray())) {
                    redactNode(root);
                    return MAPPER.writeValueAsString(root);
                }
            } catch (Exception truncatedOrInvalid) {
                // fall through to the text scan
            }
        }
        return redactText(body);
    }

    private static boolean looksLikeForm(String body) {
        return body.indexOf('=') > 0 && body.indexOf(' ') < 0 && body.indexOf('\n') < 0;
    }

    private static String redactForm(String body) {
        StringBuilder sb = new StringBuilder(body.length());
        for (String pair : body.split("&", -1)) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                sb.append(scrubValue(pair));
                continue;
            }
            String rawKey = pair.substring(0, eq);
            String key = decode(rawKey);
            String value = pair.substring(eq + 1);
            sb.append(rawKey).append('=').append(redactValue(key, decode(value), value));
        }
        return sb.toString();
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /** Text scan used for non-JSON and truncated bodies. */
    static String redactText(String text) {
        Matcher m = PAIR.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (m.find()) {
            String key = m.group(2);
            String value = m.group(4);
            String replacement;
            if (isSecretKey(key) || isCardKey(key) || isContactKey(key) || isVpaKey(key)) {
                boolean quoted = value.startsWith("\"") || value.startsWith("'");
                String inner = quoted ? unquote(value) : value;
                String redacted = redactValue(key, inner, inner);
                String shown = redacted;
                if (quoted) {
                    String q = value.substring(0, 1);
                    shown = q + redacted + (value.length() > 1 && value.endsWith(q) ? q : "");
                }
                replacement = m.group(1) + key + m.group(1) + m.group(3) + shown;
            } else {
                replacement = m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return scrubValue(sb.toString());
    }

    private static String unquote(String quoted) {
        String inner = quoted.substring(1);
        if (!inner.isEmpty() && inner.charAt(inner.length() - 1) == quoted.charAt(0)) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return inner;
    }

    private static void redactNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> field = it.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (isSecretKey(key)) {
                    field.setValue(new TextNode(MASK));
                } else if ((isCardKey(key) || isContactKey(key) || isVpaKey(key)) && value.isValueNode() && !value.isNull()) {
                    field.setValue(new TextNode(redactValue(key, value.asText(), value.asText())));
                } else if (value.isTextual()) {
                    String scrubbed = scrubValue(value.asText());
                    if (!scrubbed.equals(value.asText())) {
                        field.setValue(new TextNode(scrubbed));
                    }
                } else {
                    redactNode(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode item = array.get(i);
                if (item.isTextual()) {
                    String scrubbed = scrubValue(item.asText());
                    if (!scrubbed.equals(item.asText())) {
                        array.set(i, new TextNode(scrubbed));
                    }
                } else {
                    redactNode(item);
                }
            }
        }
    }

    /** Masks the value of a sensitive key (decoded text in, masked text out). */
    private static String redactValue(String key, String decoded, String original) {
        if (isSecretKey(key)) {
            return MASK;
        }
        if (isCardKey(key)) {
            String digits = decoded.replaceAll("\\D", "");
            return digits.length() >= 4 ? MASK + digits.substring(digits.length() - 4) : MASK;
        }
        if (isContactKey(key)) {
            return EventSanitizer.maskRecipient(decoded);
        }
        if (isVpaKey(key)) {
            return maskVpa(decoded);
        }
        return original;
    }

    /**
     * @param vpa a UPI address such as {@code name@bank}
     * @return {@code na***@bank}
     */
    public static String maskVpa(String vpa) {
        if (vpa == null || vpa.isEmpty() || vpa.contains("*")) {
            return vpa;
        }
        int at = vpa.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        return vpa.substring(0, Math.min(2, at)) + MASK + vpa.substring(at);
    }

    /** Replaces token-looking text (JWT, Bearer/Basic credentials) inside a value. */
    static String scrubValue(String value) {
        if (value == null) {
            return null;
        }
        String v = JWT_LIKE.matcher(value).replaceAll(MASK);
        return BEARER_LIKE.matcher(v).replaceAll("$1 " + MASK);
    }

    private enum Kind { NONE, SECRET, CARD, CONTACT, VPA }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Key names repeat endlessly in JSON bodies, so their classification is cached (bounded by KIND_CACHE_MAX). */
    private static final Map<String, Kind> KIND_CACHE = new ConcurrentHashMap<>();

    private static Kind kind(String key) {
        Kind k = KIND_CACHE.get(key);
        if (k == null) {
            k = classify(key);
            if (KIND_CACHE.size() < KIND_CACHE_MAX) {
                KIND_CACHE.put(key, k);
            }
        }
        return k;
    }

    private static Kind classify(String key) {
        String n = SEPARATORS.matcher(CAMEL_BOUNDARY.matcher(key).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll("");
        if (SECRET_EXACT.contains(n) || n.contains("password") || n.contains("secret") || n.contains("passwd")
                || n.endsWith("token") || n.endsWith("signature") || n.endsWith("apikey") || n.startsWith("otp")) {
            return Kind.SECRET;
        }
        if (CARD_ACCOUNT.contains(n)) {
            return Kind.CARD;
        }
        if (CONTACT.contains(n)) {
            return Kind.CONTACT;
        }
        return VPA.contains(n) ? Kind.VPA : Kind.NONE;
    }

    /** @return true for keys whose value is a credential or secret and is replaced entirely */
    static boolean isSecretKey(String key) {
        return kind(key) == Kind.SECRET;
    }

    static boolean isCardKey(String key) {
        return kind(key) == Kind.CARD;
    }

    static boolean isContactKey(String key) {
        return kind(key) == Kind.CONTACT;
    }

    static boolean isVpaKey(String key) {
        return kind(key) == Kind.VPA;
    }

    private static String clip(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    /** @return the content types whose bodies are stored (JSON, text, form posts) */
    public static boolean isCapturableContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String t = contentType.toLowerCase(Locale.ROOT);
        return t.contains("json") || t.startsWith("text/") || t.contains("x-www-form-urlencoded");
    }

}
