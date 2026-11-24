package com.heuristiq.uam.device.repository.mongo;

import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.persistence.mongo.UserDeviceDocument;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserDeviceMongoRepositoryAdapter implements UserDeviceRepository {
    private final UserDeviceMongoSpringRepository springRepository;

    UserDeviceMongoRepositoryAdapter(UserDeviceMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserDevice save(UserDevice device) {
        return toDomain(springRepository.save(toDocument(device)));
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

    private UserDevice toDomain(UserDeviceDocument doc) {
        return UserDevice.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .deviceFingerprint(doc.getDeviceFingerprint())
                .deviceUuid(doc.getDeviceUuid())
                .deviceName(doc.getDeviceName())
                .deviceType(doc.getDeviceType())
                .operatingSystem(doc.getOperatingSystem())
                .operatingSystemVersion(doc.getOperatingSystemVersion())
                .browser(doc.getBrowser())
                .browserVersion(doc.getBrowserVersion())
                .manufacturer(doc.getManufacturer())
                .model(doc.getModel())
                .pushNotificationToken(doc.getPushNotificationToken())
                .platform(doc.getPlatform())
                .trusted(doc.getTrusted())
                .verified(doc.getVerified())
                .blocked(doc.getBlocked())
                .blockReason(doc.getBlockReason())
                .lastLoginAt(doc.getLastLoginAt())
                .lastLogoutAt(doc.getLastLogoutAt())
                .lastIpAddress(doc.getLastIpAddress())
                .country(doc.getCountry())
                .state(doc.getState())
                .city(doc.getCity())
                .registeredAt(doc.getRegisteredAt())
                .lastActiveAt(doc.getLastActiveAt())
                .active(doc.getActive())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .updatedBy(doc.getUpdatedBy())
                .version(doc.getVersion())
                .build();
    }

    private UserDeviceDocument toDocument(UserDevice device) {
        return UserDeviceDocument.builder()
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
