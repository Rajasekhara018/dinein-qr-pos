package com.heuristq.dinein.realtime;

import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.security.TokenAuthenticator;
import com.heuristq.dinein.staff.domain.StaffRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP CONNECT frames and authorises every SUBSCRIBE.
 * <ul>
 *   <li>Staff: {@code Authorization: Bearer <jwt|device token>} native header on CONNECT.</li>
 *   <li>Guests: the signed guest-session cookie captured at handshake time.</li>
 * </ul>
 * Topic rules: {@code /topic/kitchen/{restaurantId}/orders} staff of that restaurant only (kitchen screens and
 * waiter screens); {@code /topic/orders/{id}} the owning guest (or owner/manager/waiter of that order's
 * restaurant); {@code /topic/staff/{restaurantId}/notifications} owner/manager users of that restaurant (not
 * kitchen devices); {@code /topic/waiter/{restaurantId}/notifications} waiter/owner/manager users of that
 * restaurant; {@code /topic/menu} anyone. A {@code platformAdmin} may only subscribe to their own restaurant's
 * topics here (the {@code X-Restaurant-Id} admin-view override is an HTTP-only mechanism; the JWT's own tenant is
 * what the socket connection carries). Clients may not SEND; the server is the only publisher.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/orders/(\\d{1,18})$");
    private static final Pattern KITCHEN_TOPIC = Pattern.compile("^/topic/kitchen/(\\d{1,18})/orders$");
    private static final Pattern STAFF_NOTIFICATIONS_TOPIC = Pattern.compile("^/topic/staff/(\\d{1,18})/notifications$");
    private static final Pattern WAITER_NOTIFICATIONS_TOPIC = Pattern.compile("^/topic/waiter/(\\d{1,18})/notifications$");

    private final TokenAuthenticator tokenAuthenticator;
    private final OrderRepository orderRepository;

    public StompAuthChannelInterceptor(TokenAuthenticator tokenAuthenticator, OrderRepository orderRepository) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.orderRepository = orderRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> throw new MessagingException("Clients cannot publish messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK... need no checks
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null) {
            UsernamePasswordAuthenticationToken auth = tokenAuthenticator.authenticate(header)
                    .orElseThrow(() -> new MessagingException("Invalid or expired token"));
            StaffPrincipal staff = (StaffPrincipal) auth.getPrincipal();
            if (staff.passwordChangeRequired()) {
                throw new MessagingException("Password change required");
            }
            accessor.setUser(auth);
            log.debug("ws.connect staff userId={} device={}", staff.userId(), staff.isDevice());
            return;
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        Object guestSessionId = attrs == null ? null : attrs.get(GuestHandshakeInterceptor.GUEST_SESSION_ATTR);
        if (guestSessionId instanceof String sid) {
            accessor.setUser(new GuestPrincipal(sid));
            log.debug("ws.connect guest");
        }
        // Anonymous connections are allowed; they can only subscribe to /topic/menu.
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        if (destination == null) {
            throw new MessagingException("Missing destination");
        }
        if (destination.equals(RealtimePublisher.MENU_TOPIC)) {
            return;
        }
        StaffPrincipal staff = staffOf(user);
        Matcher kitchen = KITCHEN_TOPIC.matcher(destination);
        if (kitchen.matches()) {
            if (staff != null && sameRestaurant(staff, kitchen.group(1))) {
                return;
            }
            throw denied(destination);
        }
        Matcher staffNotifications = STAFF_NOTIFICATIONS_TOPIC.matcher(destination);
        if (staffNotifications.matches()) {
            if (staff != null && !staff.isDevice() && sameRestaurant(staff, staffNotifications.group(1))
                    && (staff.role() == StaffRole.OWNER || staff.role() == StaffRole.MANAGER)) {
                return;
            }
            throw denied(destination);
        }
        Matcher waiterNotifications = WAITER_NOTIFICATIONS_TOPIC.matcher(destination);
        if (waiterNotifications.matches()) {
            if (staff != null && !staff.isDevice() && sameRestaurant(staff, waiterNotifications.group(1))
                    && staff.role() != StaffRole.KITCHEN) {
                return;
            }
            throw denied(destination);
        }
        Matcher m = ORDER_TOPIC.matcher(destination);
        if (m.matches()) {
            Long orderId = Long.valueOf(m.group(1));
            if (staff != null && staff.role() != StaffRole.KITCHEN) {
                return;
            }
            if (user instanceof GuestPrincipal guest && orderRepository.existsByIdAndGuestSessionId(orderId, guest.sessionId())) {
                return;
            }
        }
        throw denied(destination);
    }

    private static boolean sameRestaurant(StaffPrincipal staff, String restaurantIdSegment) {
        return staff.restaurantId() != null && staff.restaurantId().toString().equals(restaurantIdSegment);
    }

    private static StaffPrincipal staffOf(Principal user) {
        if (user instanceof UsernamePasswordAuthenticationToken token && token.getPrincipal() instanceof StaffPrincipal sp) {
            return sp;
        }
        return null;
    }

    private static MessagingException denied(String destination) {
        log.warn("ws.subscribe.denied destination={}", destination);
        return new MessagingException("Not allowed to subscribe to " + destination);
    }
}
