package com.heuristiq.gateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;

/**
 * Works out the address of the real caller for rate limiting.
 *
 * <p>Behind nginx or a load balancer the connection's address is the proxy, so every user would share one rate-limit bucket. The
 * proxy puts the caller's address in {@code X-Forwarded-For}; this class reads it, but only when it can be trusted:
 * <ul>
 *   <li>the connection must come from an internal address (loopback, private or link-local: nginx on the same host or network),
 *       because from a public address the header is whatever the caller typed, and a caller who could pick its own address could
 *       dodge every limit;</li>
 *   <li>only the entry added by the trusted proxies is used: the {@code trustedHops}-th from the right (1 = the proxy right in front
 *       of the gateway added the caller's address as the last entry; 2 = there is one more proxy in between, and so on). Entries to
 *       the left were supplied by the caller and are ignored;</li>
 *   <li>the entry must look like an IP address literal; nothing is ever resolved by name.</li>
 * </ul>
 * Anything that fails these checks falls back to the connection's own address.
 */
public final class ClientIpResolver {
    private static final Pattern IPV4 = Pattern.compile("^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    /** At least two colons, hex digits and colons only: an IPv4 address with a port ("1.2.3.4:80") is not an IPv6 address. */
    private static final Pattern IPV6 = Pattern.compile("^(?=(?:[^:]*:){2,})[0-9a-fA-F:]{2,45}$");

    private ClientIpResolver() {
    }

    /**
     * @param request     the incoming request
     * @param trustedHops how many proxies in front of the gateway add to {@code X-Forwarded-For}; 0 = trust none, use the connection address
     * @return the caller's address as text, or {@code "unknown"} when there is none
     */
    public static String resolve(ServerHttpRequest request, int trustedHops) {
        InetSocketAddress peer = request.getRemoteAddress();
        if (peer == null || peer.getAddress() == null) {
            return RateLimitConstants.UNKNOWN_ADDRESS;
        }
        String direct = peer.getAddress().getHostAddress();
        if (trustedHops < 1 || !isInternal(peer.getAddress())) {
            return direct;
        }
        String forwarded = request.getHeaders().getFirst(RateLimitConstants.HEADER_FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return direct;
        }
        String[] entries = forwarded.split(",");
        if (entries.length < trustedHops) {
            return direct;
        }
        String candidate = entries[entries.length - trustedHops].trim();
        return isAddressLiteral(candidate) ? candidate : direct;
    }

    static boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
    }

    static boolean isAddressLiteral(String text) {
        return IPV4.matcher(text).matches() || IPV6.matcher(text).matches();
    }
}
