package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationRequest.Recipients;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.staff.domain.DeviceTokenEntity;
import com.heuristq.dinein.staff.domain.DeviceTokenRepository;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Flags a kitchen device that hasn't authenticated in a while (see {@link DeviceTokenEntity#getLastSeenAt()},
 * refreshed on every REST call and STOMP (re)connect) as possibly offline. A coarse signal -- it detects "hasn't
 * successfully reconnected recently", not literally "socket currently closed" -- but a real device with a live
 * connection keeps reconnecting/authenticating often enough that sustained staleness is a meaningful alert.
 * Alerts once per outage (tracked in memory; resets on app restart) rather than every run.
 */
@Slf4j
@Component
public class DeviceOfflineMonitorJob {

    private static final Set<StaffRole> OWNER_AND_MANAGER = EnumSet.of(StaffRole.OWNER, StaffRole.MANAGER);

    private final DeviceTokenRepository deviceTokenRepository;
    private final StaffUserRepository staffUserRepository;
    private final NotificationDispatcher dispatcher;
    private final Duration threshold;
    private final Clock clock;

    /** Device ids currently believed offline, so a sustained outage alerts only once. */
    private final Set<Long> alerted = new HashSet<>();

    public DeviceOfflineMonitorJob(DeviceTokenRepository deviceTokenRepository, StaffUserRepository staffUserRepository,
                                   NotificationDispatcher dispatcher, AppProperties properties, Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.staffUserRepository = staffUserRepository;
        this.dispatcher = dispatcher;
        this.threshold = properties.notifications().deviceOfflineThreshold();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.device-offline-check-cron}", zone = "Asia/Kolkata")
    public void run() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(threshold);
        Set<Long> staleNow = new HashSet<>();
        for (DeviceTokenEntity device : deviceTokenRepository.findAllByRevokedAtIsNull()) {
            if (!device.isUsable(now)) {
                continue;
            }
            Instant lastSeen = device.getLastSeenAt();
            if (lastSeen != null && lastSeen.isBefore(staleBefore)) {
                staleNow.add(device.getId());
                if (alerted.add(device.getId())) {
                    alert(device);
                }
            }
        }
        alerted.retainAll(staleNow);
    }

    private void alert(DeviceTokenEntity device) {
        Map<String, String> data = new HashMap<>();
        data.put("deviceName", device.getDeviceName() == null || device.getDeviceName().isBlank()
                ? "A kitchen device" : device.getDeviceName());
        var ownerEmails = staffUserRepository.findAllByRestaurantIdOrderByUsernameAsc(device.getRestaurantId())
                .stream().filter(u -> u.isActive() && OWNER_AND_MANAGER.contains(u.getRole()))
                .map(StaffUserEntity::getEmail).filter(e -> e != null && !e.isBlank()).distinct().toList();
        log.warn("device.possibly_offline deviceId={} restaurantId={} lastSeenAt={}",
                device.getId(), device.getRestaurantId(), device.getLastSeenAt());
        dispatcher.dispatch(new NotificationRequest(NotificationEvent.KDS_DEVICE_OFFLINE, null, device.getRestaurantId(),
                data, new Recipients(OWNER_AND_MANAGER, ownerEmails, null, null)));
    }
}
