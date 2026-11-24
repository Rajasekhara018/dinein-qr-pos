package com.heuristiq.uam.device.repository.jpa;

import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.persistence.jpa.UserDeviceEntity;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserDeviceJpaRepositoryAdapter implements UserDeviceRepository {
    private final UserDeviceJpaSpringRepository springRepository;

    UserDeviceJpaRepositoryAdapter(UserDeviceJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserDevice save(UserDevice device) {
        return toDomain(springRepository.save(toEntity(device)));
    }

    @Override
    public Optional<UserDevice> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UserDevice> findByUserId(String userId) {
        return springRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(UserDevice device) {
        springRepository.deleteById(device.getId());
    }

    private UserDevice toDomain(UserDeviceEntity entity) {
        return UserDevice.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .deviceFingerprint(entity.getDeviceFingerprint())
                .deviceUuid(entity.getDeviceUuid())
                .deviceName(entity.getDeviceName())
                .deviceType(entity.getDeviceType())
                .operatingSystem(entity.getOperatingSystem())
                .operatingSystemVersion(entity.getOperatingSystemVersion())
                .browser(entity.getBrowser())
                .browserVersion(entity.getBrowserVersion())
                .manufacturer(entity.getManufacturer())
                .model(entity.getModel())
                .pushNotificationToken(entity.getPushNotificationToken())
                .platform(entity.getPlatform())
                .trusted(entity.getTrusted())
                .verified(entity.getVerified())
                .blocked(entity.getBlocked())
                .blockReason(entity.getBlockReason())
                .lastLoginAt(entity.getLastLoginAt())
                .lastLogoutAt(entity.getLastLogoutAt())
                .lastIpAddress(entity.getLastIpAddress())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .registeredAt(entity.getRegisteredAt())
                .lastActiveAt(entity.getLastActiveAt())
                .active(entity.getActive())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    private UserDeviceEntity toEntity(UserDevice device) {
        return UserDeviceEntity.builder()
                .id(device.getId())
                .userId(device.getUserId())
                .deviceFingerprint(device.getDeviceFingerprint())
                .deviceUuid(device.getDeviceUuid())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .operatingSystem(device.getOperatingSystem())
                .operatingSystemVersion(device.getOperatingSystemVersion())
                .browser(device.getBrowser())
                .browserVersion(device.getBrowserVersion())
                .manufacturer(device.getManufacturer())
                .model(device.getModel())
                .pushNotificationToken(device.getPushNotificationToken())
                .platform(device.getPlatform())
                .trusted(device.getTrusted())
                .verified(device.getVerified())
                .blocked(device.getBlocked())
                .blockReason(device.getBlockReason())
                .lastLoginAt(device.getLastLoginAt())
                .lastLogoutAt(device.getLastLogoutAt())
                .lastIpAddress(device.getLastIpAddress())
                .country(device.getCountry())
                .state(device.getState())
                .city(device.getCity())
                .registeredAt(device.getRegisteredAt())
                .lastActiveAt(device.getLastActiveAt())
                .active(device.getActive())
                .metadata(device.getMetadata())
                .createdAt(device.getCreatedAt())
                .createdBy(device.getCreatedBy())
                .updatedAt(device.getUpdatedAt())
                .updatedBy(device.getUpdatedBy())
                .version(device.getVersion())
                .build();
    }
}
