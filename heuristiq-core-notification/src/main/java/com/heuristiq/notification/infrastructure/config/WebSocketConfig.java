package com.railcab.nms.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Registered at /api/v1/nms/in-app/ws (not bare /ws) — NMS has no server.servlet.context-path,
 * and every controller is now versioned via ApiVersionConfig's /api/v1 prefix, so this keeps the
 * WS endpoint consistent with the gateway's no-rewrite /api/v1/nms/** route. This isn't a
 * @RestController, so ApiVersionConfig's prefix doesn't reach it — hardcoded here instead.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    // Off by default (Spring's in-memory simple broker, current/tested behavior, single-instance
    // only). Turning this on relays /topic and /queue to an external STOMP-capable broker (e.g.
    // RabbitMQ with the rabbitmq_stomp plugin) so a broadcast enqueued by whichever NMS pod
    // consumed the triggering Kafka event still reaches a user's WebSocket session held open on a
    // *different* pod -- the simple broker only ever delivers within its own JVM.
    @Value("${nms.websocket.relay.enabled:false}")
    private boolean relayEnabled;

    @Value("${nms.websocket.relay.host:localhost}")
    private String relayHost;

    @Value("${nms.websocket.relay.port:61613}")
    private int relayPort;

    @Value("${nms.websocket.relay.client-login:guest}")
    private String relayClientLogin;

    @Value("${nms.websocket.relay.client-passcode:guest}")
    private String relayClientPasscode;

    @Value("${nms.websocket.relay.system-login:guest}")
    private String relaySystemLogin;

    @Value("${nms.websocket.relay.system-passcode:guest}")
    private String relaySystemPasscode;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(NmsConfigConstants.IN_APP_WS_PATH)
                .setAllowedOriginPatterns(NmsConfigConstants.WS_ALLOWED_ORIGIN_PATTERN)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (relayEnabled) {
            registry.enableStompBrokerRelay(NmsConfigConstants.BROKER_TOPIC_PREFIX, NmsConfigConstants.BROKER_QUEUE_PREFIX)
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayClientLogin)
                    .setClientPasscode(relayClientPasscode)
                    .setSystemLogin(relaySystemLogin)
                    .setSystemPasscode(relaySystemPasscode);
        } else {
            registry.enableSimpleBroker(NmsConfigConstants.BROKER_TOPIC_PREFIX, NmsConfigConstants.BROKER_QUEUE_PREFIX);
        }
        registry.setApplicationDestinationPrefixes(NmsConfigConstants.APP_DESTINATION_PREFIX);
        registry.setUserDestinationPrefix(NmsConfigConstants.USER_DESTINATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }
}
