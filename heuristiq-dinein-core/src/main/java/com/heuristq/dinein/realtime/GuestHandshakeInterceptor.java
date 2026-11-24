package com.heuristq.dinein.realtime;

import com.heuristq.dinein.guest.GuestSessionService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the guest session from the HttpOnly cookie during the WebSocket handshake (browsers send cookies on the
 * upgrade request; JavaScript cannot read them to put in a STOMP header).
 */
@Component
public class GuestHandshakeInterceptor implements HandshakeInterceptor {

    public static final String GUEST_SESSION_ATTR = "guestSessionId";

    private final GuestSessionService guestSessionService;

    public GuestHandshakeInterceptor(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            guestSessionService.fromRequest(servletRequest.getServletRequest())
                    .ifPresent(s -> attributes.put(GUEST_SESSION_ATTR, s.sessionId()));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // nothing to do
    }
}
