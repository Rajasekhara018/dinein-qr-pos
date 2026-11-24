package com.heuristiq.paymentservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Decides whether a request came from a caller the gateway marked as holding the elevated-access privilege
 * (PLATFORM_ELEVATED_ACCESS). The gateway sets {@value #ELEVATED_HEADER} only after its own privilege check and
 * sends its shared forward key in {@value #GATEWAY_TOKEN_HEADER}; the header is honoured only when that key
 * matches, so a request that did not pass through the gateway can never be elevated. With no key configured nobody
 * is elevated.
 */
@Component
public class GatewayElevation {

    public static final String ELEVATED_HEADER = SecurityConstants.ELEVATED_HEADER;
    public static final String GATEWAY_TOKEN_HEADER = SecurityConstants.GATEWAY_TOKEN_HEADER;

    private final byte[] forwardKey;

    public GatewayElevation(@Value("${gateway.forward-key:}") String forwardKey) {
        this.forwardKey = forwardKey == null ? new byte[0] : forwardKey.getBytes(StandardCharsets.UTF_8);
    }

    /** @return true when the gateway vouched for this request's caller as elevated */
    public boolean isElevated(HttpServletRequest request) {
        if (forwardKey.length == 0 || !Boolean.parseBoolean(request.getHeader(ELEVATED_HEADER))) {
            return false;
        }
        String token = request.getHeader(GATEWAY_TOKEN_HEADER);
        return token != null && MessageDigest.isEqual(forwardKey, token.getBytes(StandardCharsets.UTF_8));
    }
}
