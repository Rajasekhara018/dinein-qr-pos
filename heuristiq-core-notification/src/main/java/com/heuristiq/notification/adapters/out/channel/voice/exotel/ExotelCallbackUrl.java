package com.railcab.nms.adapters.out.channel.voice.exotel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the status callback URL handed to Exotel. Exotel cannot add a custom header, so the callback proves itself with HTTP
 * basic credentials carried in the URL's user info (sent by Exotel as an {@code Authorization} header, which the gateway's
 * audit redaction masks), the password being the NMS callback shared secret.
 */
public final class ExotelCallbackUrl {

    private static final String SCHEME_SEPARATOR = "://";

    private ExotelCallbackUrl() {
    }

    /**
     * The URL with {@code nms:<secret>@} added after the scheme, unless it already has user info, the secret is blank, or
     * the URL is blank (then it is returned as it is; blank means "no callback").
     */
    public static String withCredentials(String url, String sharedSecret) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        int schemeEnd = trimmed.indexOf(SCHEME_SEPARATOR);
        if (sharedSecret == null || sharedSecret.isBlank() || schemeEnd < 0) {
            return trimmed;
        }
        int authorityStart = schemeEnd + SCHEME_SEPARATOR.length();
        int authorityEnd = indexOfAny(trimmed, authorityStart);
        if (trimmed.substring(authorityStart, authorityEnd).contains("@")) {
            return trimmed;
        }
        String password = URLEncoder.encode(sharedSecret, StandardCharsets.UTF_8).replace("+", "%20");
        return trimmed.substring(0, authorityStart) + ExotelConstants.CALLBACK_BASIC_USER + ":" + password + "@"
                + trimmed.substring(authorityStart);
    }

    private static int indexOfAny(String value, int from) {
        for (int i = from; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                return i;
            }
        }
        return value.length();
    }
}
