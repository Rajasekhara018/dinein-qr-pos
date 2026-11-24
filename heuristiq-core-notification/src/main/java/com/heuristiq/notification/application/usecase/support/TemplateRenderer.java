package com.railcab.nms.application.usecase.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Renders a {@link NotificationTemplate} body by:
 * 1. Base64-decoding the stored body
 * 2. Replacing every {@code {{key}}} placeholder with the matching value from the variables map
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /**
     * Decodes and renders the template body.
     *
     * @param base64Body  Base64-encoded template body
     * @param variables   key → value substitution map
     * @return rendered string (placeholders not found in the map are left as-is)
     */
    public String render(String base64Body, Map<String, String> variables) {
        if (base64Body == null || base64Body.isBlank()) return "";

        String decoded;
        try {
            decoded = new String(Base64.getMimeDecoder().decode(base64Body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Body was already plain text — use as-is
            decoded = base64Body;
        }

        return renderText(decoded, variables);
    }

    /**
     * Substitutes {@code {{key}}} placeholders in text that is already plain (not Base64), the way
     * {@link #render} does for stored bodies. Placeholders without a value are left as typed.
     */
    public String renderText(String text, Map<String, String> variables) {
        if (text == null || text.isEmpty()) return "";
        if (variables == null || variables.isEmpty()) return text;

        StringBuffer result = new StringBuffer();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            String key = m.group(1);
            String replacement = variables.getOrDefault(key, m.group(0));
            m.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(result);
        return result.toString();
    }
}
