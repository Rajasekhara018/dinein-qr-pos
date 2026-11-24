package com.heuristiq.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps a service's service-token-only ({@code /internal/}) endpoints off the public surface: a route filter that
 * answers 404, before the request is routed, for every spelling of such a path. The services still demand the service
 * token themselves; this is the second wall, so an end user can never reach those endpoints through the gateway.
 *
 * <p>A path is normalised before matching, so it can't be smuggled past the pattern: percent-decoding is repeated
 * (up to three times, for double-encoded input), the path is lower-cased, back-slashes become slashes, repeated
 * slashes collapse, {@code ;matrix} parameters are dropped from each segment, and {@code .}/{@code ..} segments are
 * resolved -- all things the downstream servlet container would also do before dispatching.
 */
public final class InternalPathGuard {

    private static final int MAX_DECODE_ROUNDS = 3;

    private InternalPathGuard() {
    }

    /**
     * @param rawPath the request path as the client sent it (may be null)
     * @param internalPath pattern matched with {@code find()} against the normalised, lower-case path
     * @return true when the normalised path matches {@code internalPath}
     */
    public static boolean isInternalPath(String rawPath, Pattern internalPath) {
        if (rawPath == null) {
            return false;
        }
        return internalPath.matcher(normalise(rawPath)).find();
    }

    /**
     * @param internalPath pattern of the internal endpoints to refuse (see {@link #isInternalPath})
     * @return a route filter answering 404 without forwarding for matching paths, and passing everything else on
     */
    public static GatewayFilter blockInternalPaths(Pattern internalPath) {
        return (exchange, chain) -> {
            if (isInternalPath(exchange.getRequest().getURI().getRawPath(), internalPath)) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    /** Decoded, lower-case, slash-collapsed path with matrix parameters dropped and dot segments resolved. */
    static String normalise(String rawPath) {
        String p = rawPath;
        for (int i = 0; i < MAX_DECODE_ROUNDS; i++) {
            String decoded;
            try {
                decoded = UriUtils.decode(p, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformed) {
                break;
            }
            if (decoded.equals(p)) {
                break;
            }
            p = decoded;
        }
        p = p.toLowerCase(Locale.ROOT).replace('\\', '/');
        boolean leadingSlash = p.startsWith("/");
        boolean trailingSlash = p.endsWith("/") && p.length() > 1;
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : p.split("/")) {
            int matrix = segment.indexOf(';');
            String s = matrix >= 0 ? segment.substring(0, matrix) : segment;
            if (s.isEmpty() || s.equals(".")) {
                continue;
            }
            if (s.equals("..")) {
                segments.pollLast();
                continue;
            }
            segments.addLast(s);
        }
        String joined = String.join("/", segments);
        StringBuilder sb = new StringBuilder();
        if (leadingSlash) {
            sb.append('/');
        }
        sb.append(joined);
        if (trailingSlash && !joined.isEmpty()) {
            sb.append('/');
        }
        return sb.toString();
    }
}
