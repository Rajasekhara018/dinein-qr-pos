package com.heuristiq.uam.application.client;

import com.heuristiq.uam.common.UamException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the values of an API client that the screen cannot be trusted with: the client id and the redirect URIs. Pure and
 * stateless; every failure is a 400 with a message the form can show as given.
 */
public final class ApiClientValidator {

    private static final Pattern CLIENT_ID = Pattern.compile(ApiClientConstants.CLIENT_ID_REGEX);

    private ApiClientValidator() {
    }

    /** @throws UamException when the client id is not 3 to 64 letters, digits, dots, underscores or hyphens starting with a letter or digit */
    public static void requireValidClientId(String clientId) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw new UamException("Client id must be 3 to 64 characters: letters, digits, '.', '_' or '-', starting with a letter or digit");
        }
    }

    /**
     * Every URI must be an absolute http or https URL with a host, no user info and no fragment, https unless the host is
     * localhost or 127.0.0.1, at most {@link ApiClientConstants#MAX_REDIRECT_URI_LENGTH} characters; at most
     * {@link ApiClientConstants#MAX_REDIRECT_URIS} of them.
     */
    public static void requireValidRedirectUris(Set<String> redirectUris) {
        if (redirectUris == null) {
            return;
        }
        if (redirectUris.size() > ApiClientConstants.MAX_REDIRECT_URIS) {
            throw new UamException("A client may have at most " + ApiClientConstants.MAX_REDIRECT_URIS + " redirect URIs");
        }
        redirectUris.forEach(ApiClientValidator::requireValidRedirectUri);
    }

    private static void requireValidRedirectUri(String value) {
        if (value.length() > ApiClientConstants.MAX_REDIRECT_URI_LENGTH) {
            throw invalid("is longer than " + ApiClientConstants.MAX_REDIRECT_URI_LENGTH + " characters", value);
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw invalid("is not a valid URL", value);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ApiClientConstants.SCHEME_HTTP.equals(scheme) && !ApiClientConstants.SCHEME_HTTPS.equals(scheme)) {
            throw invalid("must start with http:// or https://", value);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw invalid("needs a host name", value);
        }
        if (uri.getUserInfo() != null) {
            throw invalid("must not contain a user name or password", value);
        }
        if (uri.getFragment() != null) {
            throw invalid("must not contain a '#' fragment", value);
        }
        if (ApiClientConstants.SCHEME_HTTP.equals(scheme) && !ApiClientConstants.LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw invalid("must use https (http is only allowed for localhost)", value);
        }
    }

    private static UamException invalid(String reason, String value) {
        return new UamException("Redirect URI " + reason + ": " + abbreviated(value));
    }

    /** The value as shown in a message: control characters and very long text are cut so a message stays one readable line. */
    private static String abbreviated(String value) {
        String clean = value.replaceAll("\\p{Cntrl}", "?");
        return clean.length() > 80 ? clean.substring(0, 80) + "..." : clean;
    }
}
