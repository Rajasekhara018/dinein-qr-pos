package com.railcab.nms.adapters.out.persistence.jpa;

import com.railcab.nms.domain.repository.TenantScope;
import com.railcab.nms.domain.model.DeviceRegistration;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.railcab.nms.domain.repository.IDeviceTokenRepository;
import com.railcab.nms.adapters.out.persistence.jpa.DeviceTokenEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class DeviceTokenJpaRepositoryAdapter implements IDeviceTokenRepository {

    private final DeviceTokenJpaSpringRepository springRepository;

    /** A person rarely has more than a few devices; older registrations beyond this are dropped. */
    @Value("${nms.push.max-tokens-per-user:10}")
    private int maxTokensPerUser = 10;

    DeviceTokenJpaRepositoryAdapter(DeviceTokenJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    @Transactional
    public void register(String userId, String token, DeviceRegistration details) {
        springRepository.deleteByTokenAndUserIdNot(token, userId);
        LocalDateTime now = LocalDateTime.now();
        DeviceTokenEntity entity = springRepository.findFirstByUserIdAndToken(userId, token).orElseGet(() -> {
            DeviceTokenEntity created = new DeviceTokenEntity();
            created.setUserId(userId);
            created.setToken(token);
            created.setCreatedAt(now);
            return created;
        });
        entity.setPlatform(details.platform());
        entity.setUserType(details.userType());
        entity.setApp(details.app());
        entity.setDeviceModel(details.deviceModel());
        entity.setOsVersion(details.osVersion());
        entity.setAppVersion(details.appVersion());
        entity.setLocale(details.locale());
        entity.setTimezone(details.timezone());
        entity.setTenantId(details.tenantId());
        entity.setLastSeenAt(now);
        springRepository.save(entity);
        pruneOldest(userId);
    }

    /** Keeps only the most recently active {@code maxTokensPerUser} registrations of this user. */
    private void pruneOldest(String userId) {
        List<DeviceTokenEntity> all = new ArrayList<>(springRepository.findByUserId(userId));
        if (all.size() <= maxTokensPerUser) {
            return;
        }
        all.sort(Comparator.comparing(DeviceTokenJpaRepositoryAdapter::lastActivity).reversed());
        springRepository.deleteAll(all.subList(maxTokensPerUser, all.size()));
    }

    private static LocalDateTime lastActivity(DeviceTokenEntity entity) {
        if (entity.getLastSeenAt() != null) return entity.getLastSeenAt();
        return entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.MIN;
    }

    @Override
    @Transactional
    public void unregisterToken(String token) {
        springRepository.deleteByToken(token);
    }

    @Override
    public void unregister(String userId, String token) {
        springRepository.deleteByUserIdAndToken(userId, token);
    }

    @Override
    public List<String> findTokensByUserIds(Collection<String> userIds, TenantScope scope) {
        if (scope.unrestricted()) {
            return findTokensByUserIds(userIds);
        }
        return springRepository.findByUserIdInScoped(userIds, scope.tenantIdOrEmpty()).stream()
                .map(DeviceTokenEntity::getToken)
                .distinct()
                .toList();
    }

    @Override
    public List<String> findTokensByUserIds(Collection<String> userIds) {
        return springRepository.findByUserIdIn(userIds).stream()
                .map(DeviceTokenEntity::getToken)
                .distinct()
                .toList();
    }
}
