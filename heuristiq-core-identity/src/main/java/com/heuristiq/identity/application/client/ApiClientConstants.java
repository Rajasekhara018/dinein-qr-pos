package com.heuristiq.uam.application.client;

import java.util.Set;

/** Fixed values of the API client rules: the shape of a client id and what a redirect URI may be. */
public final class ApiClientConstants {

    /** A client id: starts with a letter or digit, then letters, digits, dot, underscore or hyphen; 3 to 64 characters. */
    public static final String CLIENT_ID_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$";

    /** Most redirect URIs one client may carry. */
    public static final int MAX_REDIRECT_URIS = 20;

    /**
     * Longest redirect URI. The storage column ({@code api_client_redirect_uris.redirect_uri}) is 255 characters wide, so a
     * longer value would fail at the database instead of with a clear message.
     */
    public static final int MAX_REDIRECT_URI_LENGTH = 255;

    /** The only schemes a redirect URI may use. */
    public static final String SCHEME_HTTP = "http";
    public static final String SCHEME_HTTPS = "https";

    /** Hosts that may use plain http (local development); every other host needs https. */
    public static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1");

    private ApiClientConstants() {
    }
}
