package com.heuristq.dinein.notification;

import com.heuristq.dinein.guest.GuestSession;
import com.heuristq.dinein.notification.NotificationRequest.PushTarget;
import com.heuristq.dinein.notification.domain.PushOwnerType;
import com.heuristq.dinein.notification.domain.PushSubscriptionEntity;
import com.heuristq.dinein.notification.domain.PushSubscriptionRepository;
import com.heuristq.dinein.notification.dto.NotificationDtos.GuestPushSubscriptionRequest;
import com.heuristq.dinein.notification.dto.NotificationDtos.NotificationConfigResponse;
import com.heuristq.dinein.notification.dto.NotificationDtos.StaffPushSubscriptionRequest;
import com.heuristq.dinein.notification.push.PushSenderRegistry;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Device registrations for push. A token is unique per provider, so registering it again (new login, new order)
 * moves it to the new owner. Guest devices are matched by guest session, which covers every order of that session.
 */
@Slf4j
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final OrderRepository orderRepository;
    private final PushSenderRegistry pushSenders;
    private final NotificationProperties properties;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository repository, OrderRepository orderRepository,
                                   PushSenderRegistry pushSenders, NotificationProperties properties, Clock clock) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.pushSenders = pushSenders;
        this.properties = properties;
        this.clock = clock;
    }

    /** Tells the browser whether to ask for push permission and how to obtain a token. */
    public NotificationConfigResponse clientConfig() {
        String provider = pushSenders.activeProvider();
        if (!properties.push().enabled() || SenderRegistry.LOG.equals(provider)) {
            return new NotificationConfigResponse(false, null, null);
        }
        return new NotificationConfigResponse(true, provider, pushSenders.active().publicClientConfig());
    }

    @Transactional
    public void registerStaff(StaffPrincipal staff, StaffPushSubscriptionRequest request) {
        String provider = requireActiveProvider(request.provider());
        upsert(provider, request.token().trim(), PushOwnerType.STAFF_USER, String.valueOf(staff.userId()), null,
                request.platform());
        log.info("push.subscribed ownerType=STAFF_USER userId={} provider={}", staff.userId(), provider);
    }

    @Transactional
    public void unregisterStaff(StaffPrincipal staff, StaffPushSubscriptionRequest request) {
        repository.findByProviderAndToken(request.provider().trim().toUpperCase(Locale.ROOT), request.token().trim())
                .filter(s -> s.getOwnerType() == PushOwnerType.STAFF_USER
                        && s.getOwnerId().equals(String.valueOf(staff.userId())))
                .ifPresent(s -> {
                    s.setActive(false);
                    log.info("push.unsubscribed ownerType=STAFF_USER userId={} subscriptionId={}", staff.userId(), s.getId());
                });
    }

    @Transactional
    public void registerGuest(GuestSession guest, GuestPushSubscriptionRequest request, String platform) {
        String provider = requireActiveProvider(request.provider());
        if (!orderRepository.existsByIdAndGuestSessionId(request.orderId(), guest.sessionId())) {
            throw ApiException.notFound("Order");
        }
        upsert(provider, request.token().trim(), PushOwnerType.GUEST_SESSION, guest.sessionId(), request.orderId(),
                platform);
        log.info("push.subscribed ownerType=GUEST_SESSION orderId={} provider={}", request.orderId(), provider);
    }

    @Transactional(readOnly = true)
    public List<PushTarget> guestTargets(String guestSessionId) {
        return repository.findByOwnerTypeAndOwnerIdAndActiveTrue(PushOwnerType.GUEST_SESSION, guestSessionId).stream()
                .map(PushSubscriptionService::target).toList();
    }

    @Transactional(readOnly = true)
    public List<PushTarget> staffTargets(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = userIds.stream().map(String::valueOf).toList();
        return repository.findByOwnerTypeAndOwnerIdInAndActiveTrue(PushOwnerType.STAFF_USER, ids).stream()
                .map(PushSubscriptionService::target).toList();
    }

    @Transactional
    public void markUsed(Long subscriptionId) {
        repository.findById(subscriptionId).ifPresent(s -> s.setLastUsedAt(clock.instant()));
    }

    @Transactional
    public void deactivate(Long subscriptionId) {
        repository.findById(subscriptionId).ifPresent(s -> {
            s.setActive(false);
            log.info("push.deactivated subscriptionId={} reason=unregistered", subscriptionId);
        });
    }

    private void upsert(String provider, String token, PushOwnerType ownerType, String ownerId, Long orderId,
                        String platform) {
        PushSubscriptionEntity s = repository.findByProviderAndToken(provider, token).orElseGet(() -> {
            PushSubscriptionEntity created = new PushSubscriptionEntity();
            created.setProvider(provider);
            created.setToken(token);
            return created;
        });
        s.setOwnerType(ownerType);
        s.setOwnerId(ownerId);
        s.setOrderId(orderId);
        String p = platform == null || platform.isBlank() ? null : platform.trim();
        s.setPlatform(p != null && p.length() > 120 ? p.substring(0, 120) : p);
        s.setActive(true);
        s.setLastUsedAt(clock.instant());
        repository.save(s);
    }

    private String requireActiveProvider(String requested) {
        if (!properties.push().enabled()) {
            throw ApiException.conflict("PUSH_DISABLED", "Push notifications are not enabled");
        }
        String provider = requested.trim().toUpperCase(Locale.ROOT);
        if (!provider.equals(pushSenders.activeProvider())) {
            throw ApiException.badRequest("PUSH_PROVIDER_MISMATCH",
                    "Push provider " + provider + " is not active (active: " + pushSenders.activeProvider() + ")");
        }
        return provider;
    }

    private static PushTarget target(PushSubscriptionEntity s) {
        return new PushTarget(s.getId(), s.getProvider(), s.getToken());
    }
}
