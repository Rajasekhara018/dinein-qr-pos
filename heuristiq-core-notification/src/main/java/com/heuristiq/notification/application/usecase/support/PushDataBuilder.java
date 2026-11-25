package com.heuristiq.notification.application.usecase.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.domain.model.NotificationChannel;

/**
 * Builds the structured data map that travels with a push (and, for VOICE, the call's metadata) from a
 * {@link NotificationRequest}, per the push data contract: {@code type}, {@code eventCode}, {@code entityType},
 * {@code entityId}, {@code rideId}, {@code url} and every template variable except sensitive ones. Each value is capped at
 * {@link PushDataConstants#MAX_VALUE_LENGTH} characters and the whole map at {@link PushDataConstants#MAX_TOTAL_BYTES}
 * bytes; variables are dropped last-in first when over. Pure and stateless.
 */
public final class PushDataBuilder {

    private PushDataBuilder() {
    }

    /** The contract map for a push. Never null; the fixed keys come first, then the variables in their given order. */
    public static Map<String, String> build(NotificationRequest request) {
        Map<String, String> fixed = new LinkedHashMap<>();
        if (request == null) {
            fixed.put(PushDataConstants.KEY_URL, PushDataConstants.URL_NOTIFICATIONS);
            return fixed;
        }
        putIfText(fixed, PushDataConstants.KEY_TYPE,
                request.getEvent() == null ? null : request.getEvent().trim().toUpperCase(Locale.ROOT));
        putIfText(fixed, PushDataConstants.KEY_EVENT_CODE, request.getEventCode());
        putIfText(fixed, PushDataConstants.KEY_ENTITY_TYPE, request.getEntityType());
        putIfText(fixed, PushDataConstants.KEY_ENTITY_ID, request.getEntityId());
        putIfText(fixed, PushDataConstants.KEY_RIDE_ID, rideId(request));
        fixed.put(PushDataConstants.KEY_URL, PushDataConstants.URL_NOTIFICATIONS);
        fixed.replaceAll((key, value) -> cap(value));

        List<Map.Entry<String, String>> variables = new ArrayList<>();
        if (request.getVariables() != null) {
            for (Map.Entry<String, String> entry : request.getVariables().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank() || entry.getValue() == null || fixed.containsKey(key) || isSensitive(key)) {
                    continue;
                }
                variables.add(Map.entry(key, cap(entry.getValue())));
            }
        }
        int total = size(fixed.entrySet()) + size(variables);
        while (total > PushDataConstants.MAX_TOTAL_BYTES && !variables.isEmpty()) {
            Map.Entry<String, String> dropped = variables.remove(variables.size() - 1);
            total -= bytes(dropped.getKey()) + bytes(dropped.getValue());
        }
        Map<String, String> data = new LinkedHashMap<>(fixed);
        variables.forEach(entry -> data.put(entry.getKey(), entry.getValue()));
        return data;
    }

    /**
     * The map handed to a channel provider: the contract map, plus the NMS message id for VOICE (the call carries it so
     * the provider's callback can be traced back to the message). Providers other than push and voice ignore it.
     */
    public static Map<String, String> forChannel(NotificationChannel channel, NotificationRequest request) {
        Map<String, String> data = build(request);
        if (channel == NotificationChannel.VOICE && request != null) {
            putIfText(data, PushDataConstants.KEY_MESSAGE_ID, request.getMessageId());
        }
        return data;
    }

    /** True when the variable name contains a sensitive word (otp, password, secret, token, pin, cvv), in any case. */
    public static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return PushDataConstants.SENSITIVE_KEY_PARTS.stream().anyMatch(lower::contains);
    }

    private static String rideId(NotificationRequest request) {
        if (PushDataConstants.ENTITY_TYPE_RIDE.equalsIgnoreCase(request.getEntityType()) && hasText(request.getEntityId())) {
            return request.getEntityId();
        }
        Map<String, String> variables = request.getVariables();
        String fromVariables = variables == null ? null : variables.get(PushDataConstants.KEY_RIDE_ID);
        return hasText(fromVariables) ? fromVariables : null;
    }

    private static void putIfText(Map<String, String> map, String key, String value) {
        if (hasText(value)) {
            map.put(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String cap(String value) {
        return value.length() <= PushDataConstants.MAX_VALUE_LENGTH ? value : value.substring(0, PushDataConstants.MAX_VALUE_LENGTH);
    }

    private static int size(Iterable<? extends Map.Entry<String, String>> entries) {
        int total = 0;
        for (Map.Entry<String, String> entry : entries) {
            total += bytes(entry.getKey()) + bytes(entry.getValue());
        }
        return total;
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
