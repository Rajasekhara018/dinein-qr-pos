package com.heuristiq.identity.auth;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * The address of the person making a request, for the device / session / audit records.
 *
 * <p>UAM sits behind a reverse proxy and the API gateway, so the connection's own address
 * ({@code getRemoteAddr()}) is a Docker-internal one such as {@code 172.23.0.2}, which tells the user
 * nothing about where they signed in from. The proxies pass the real address on in
 * {@code X-Forwarded-For} (client first, each proxy appending the peer it saw) and {@code X-Real-IP}.
 * This picks, in order: the first <em>public</em> address in X-Forwarded-For, a public X-Real-IP, the
 * first X-Forwarded-For address of any kind (so an office or home LAN address still shows when
 * testing from inside the network), and only then the connection address.
 *
 * <p>These headers are set by the proxies, not trusted blindly from the internet: the front proxy
 * must overwrite or append to them (see the nginx note in the deployment docs), otherwise a client
 * could send its own value. Addresses are only parsed as literals, never resolved through DNS.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        List<String> forwarded = new ArrayList<>();
        String header = request.getHeader(AuthConstants.FORWARDED_FOR_HEADER);
        if (header != null) {
            for (String part : header.split(",")) {
                String ip = clean(part);
                if (ip != null) forwarded.add(ip);
            }
        }
        for (String ip : forwarded) {
            if (!isPrivateOrLocal(ip)) return ip;
        }
        String realIp = clean(request.getHeader(AuthConstants.REAL_IP_HEADER));
        if (realIp != null && !isPrivateOrLocal(realIp)) return realIp;
        if (!forwarded.isEmpty()) return forwarded.get(0);
        if (realIp != null) return realIp;
        return request.getRemoteAddr();
    }

    /** Strips whitespace, a port, and IPv6 brackets; null if it is not an IP literal. */
    static String clean(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || AuthConstants.UNKNOWN_ADDRESS.equalsIgnoreCase(value)) return null;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            value = end > 0 ? value.substring(1, end) : value.substring(1);
        } else if (value.indexOf(':') >= 0 && value.indexOf(':') == value.lastIndexOf(':')) {
            value = value.substring(0, value.indexOf(':')); // IPv4 with a port
        }
        return isIpLiteral(value) ? value : null;
    }

    private static boolean isIpLiteral(String value) {
        if (value.length() > AuthConstants.MAX_IP_LITERAL_LENGTH) return false;
        for (char c : value.toCharArray()) {
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '.' || c == ':';
            if (!ok) return false;
        }
        return value.contains(".") || value.contains(":");
    }

    /** Loopback, link-local, private (RFC 1918 / unique-local) and unspecified addresses. */
    static boolean isPrivateOrLocal(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip); // a literal: no DNS lookup happens
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            // IPv6 unique-local fc00::/7, and IPv4 carrier-grade NAT 100.64.0.0/10
            if (bytes.length == 16) return (bytes[0] & 0xFE) == 0xFC;
            return bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
