package com.railcab.nms.infrastructure.config;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Authenticates the STOMP CONNECT frame's {@code Authorization} native header — this runs
 * inside the WebSocket connection itself, independent of the gateway's HTTP-level JWT filter
 * (which never sees this header: browsers can't set custom headers on a WS/SockJS handshake).
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtSupport jwtSupport;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
            jwtSupport.parseBearer(header).ifPresent(principal -> accessor.setUser(
                    new UsernamePasswordAuthenticationToken(
                            principal.userId(),
                            null,
                            principal.roles().stream().map(role -> new SimpleGrantedAuthority(NmsConfigConstants.ROLE_PREFIX + role)).toList())));
        }
        return message;
    }
}
