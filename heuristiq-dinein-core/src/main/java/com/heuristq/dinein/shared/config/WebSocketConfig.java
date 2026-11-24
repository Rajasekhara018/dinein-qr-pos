package com.heuristq.dinein.shared.config;

import com.heuristq.dinein.realtime.GuestHandshakeInterceptor;
import com.heuristq.dinein.realtime.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

/**
 * STOMP over native WebSocket at {@code /ws} with an in-memory broker. WebSocket is only a notification channel:
 * clients refetch REST state on (re)connect, which remains the source of truth.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;
    private final GuestHandshakeInterceptor guestHandshakeInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor,
                           GuestHandshakeInterceptor guestHandshakeInterceptor, AppProperties properties) {
        this.authInterceptor = authInterceptor;
        this.guestHandshakeInterceptor = guestHandshakeInterceptor;
        this.allowedOrigins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of() : properties.cors().allowedOrigins();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("ws-heartbeat-");
        heartbeatScheduler.initialize();
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same-origin through Nginx / the dev proxy; explicit origins guard against cross-site WebSocket hijacking.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
                .addInterceptors(guestHandshakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024).setSendBufferSizeLimit(512 * 1024).setSendTimeLimit(15_000);
    }
}
