package com.heuristq.dinein.staff;

import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.security.TokenAuthenticator;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.staff.domain.DeviceTokenEntity;
import com.heuristq.dinein.staff.domain.DeviceTokenRepository;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.staff.dto.DeviceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Long-lived, revocable kitchen device sessions. Devices always act with the KITCHEN role only. */
@Slf4j
@Service
public class DeviceTokenService {

    private static final Duration LAST_SEEN_RESOLUTION = Duration.ofMinutes(5);

    private final DeviceTokenRepository deviceTokenRepository;
    private final StaffUserRepository staffUserRepository;
    private final Duration ttl;
    private final Clock clock;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository, StaffUserRepository staffUserRepository,
                              AppProperties properties, Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.staffUserRepository = staffUserRepository;
        this.ttl = properties.security().deviceTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public IssuedDeviceToken issue(StaffUserEntity user, String deviceName) {
        String raw = TokenAuthenticator.DEVICE_TOKEN_PREFIX + SecureTokens.randomUrlSafe(32);
        DeviceTokenEntity entity = new DeviceTokenEntity();
        entity.setStaffUserId(user.getId());
        entity.setDeviceName(deviceName);
        entity.setTokenHash(SecureTokens.sha256Hex(raw));
        entity.setExpiresAt(clock.instant().plus(ttl));
        entity.setLastSeenAt(clock.instant());
        deviceTokenRepository.save(entity);
        log.info("device.issued deviceId={} userId={}", entity.getId(), user.getId());
        return new IssuedDeviceToken(raw, entity.getExpiresAt());
    }

    @Transactional
    public Optional<StaffPrincipal> authenticate(String rawToken) {
        Instant now = clock.instant();
        return deviceTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(device -> device.isUsable(now))
                .flatMap(device -> staffUserRepository.findById(device.getStaffUserId())
                        .filter(StaffUserEntity::isActive)
                        .map(user -> {
                            if (device.getLastSeenAt() == null
                                    || device.getLastSeenAt().plus(LAST_SEEN_RESOLUTION).isBefore(now)) {
                                device.setLastSeenAt(now);
                            }
                            return new StaffPrincipal(user.getId(), user.getUsername(), StaffRole.KITCHEN,
                                    device.getId(), false);
                        }));
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        List<DeviceTokenEntity> devices = deviceTokenRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, StaffUserEntity> users = staffUserRepository
                .findAllById(devices.stream().map(DeviceTokenEntity::getStaffUserId).distinct().toList())
                .stream().collect(Collectors.toMap(StaffUserEntity::getId, Function.identity()));
        Instant now = clock.instant();
        return devices.stream().map(d -> {
            StaffUserEntity u = users.get(d.getStaffUserId());
            return new DeviceResponse(d.getId(), d.getDeviceName(), u == null ? null : u.getUsername(),
                    d.getCreatedAt(), d.getLastSeenAt(), d.getExpiresAt(), d.getRevokedAt(), d.isUsable(now));
        }).toList();
    }

    @Transactional
    public void revoke(Long id) {
        DeviceTokenEntity device = deviceTokenRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Device"));
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(clock.instant());
            log.info("device.revoked deviceId={}", id);
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        Instant now = clock.instant();
        deviceTokenRepository.findByStaffUserIdAndRevokedAtIsNull(userId).forEach(d -> d.setRevokedAt(now));
    }

    public record IssuedDeviceToken(String token, Instant expiresAt) {
    }
}
