package com.railcab.nms.adapters.out.persistence.mongo;

import com.railcab.nms.domain.repository.TenantScope;
import com.railcab.nms.domain.model.DeviceRegistration;

import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.railcab.nms.domain.repository.IDeviceTokenRepository;
import com.railcab.nms.adapters.out.persistence.mongo.DeviceTokenDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class DeviceTokenMongoRepositoryAdapter implements IDeviceTokenRepository {

    private final DeviceTokenMongoSpringRepository springRepository;

    /** A person rarely has more than a few devices; older registrations beyond this are dropped. */
    @Value("${nms.push.max-tokens-per-user:10}")
    private int maxTokensPerUser = 10;

    DeviceTokenMongoRepositoryAdapter(DeviceTokenMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public void register(String userId, String token, DeviceRegistration details) {
        springRepository.deleteByTokenAndUserIdNot(token, userId);
        LocalDateTime now = LocalDateTime.now();
        DeviceTokenDocument doc = springRepository.findFirstByUserIdAndToken(userId, token).orElseGet(() -> {
            DeviceTokenDocument created = new DeviceTokenDocument();
            created.setUserId(userId);
            created.setToken(token);
            created.setCreatedAt(now);
            return created;
        });
        doc.setPlatform(details.platform());
        doc.setUserType(details.userType());
        doc.setApp(details.app());
        doc.setDeviceModel(details.deviceModel());
        doc.setOsVersion(details.osVersion());
        doc.setAppVersion(details.appVersion());
        doc.setLocale(details.locale());
        doc.setTimezone(details.timezone());
        doc.setTenantId(details.tenantId());
        doc.setLastSeenAt(now);
        springRepository.save(doc);
        pruneOldest(userId);
    }

    /** Keeps only the most recently active {@code maxTokensPerUser} registrations of this user. */
    private void pruneOldest(String userId) {
        List<DeviceTokenDocument> all = new ArrayList<>(springRepository.findByUserId(userId));
        if (all.size() <= maxTokensPerUser) {
            return;
        }
        all.sort(Comparator.comparing(DeviceTokenMongoRepositoryAdapter::lastActivity).reversed());
        springRepository.deleteAll(all.subList(maxTokensPerUser, all.size()));
    }

    private static LocalDateTime lastActivity(DeviceTokenDocument doc) {
        if (doc.getLastSeenAt() != null) return doc.getLastSeenAt();
        return doc.getCreatedAt() != null ? doc.getCreatedAt() : LocalDateTime.MIN;
    }

    @Override
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
                .map(DeviceTokenDocument::getToken)
                .distinct()
                .toList();
    }

    @Override
    public List<String> findTokensByUserIds(Collection<String> userIds) {
        return springRepository.findByUserIdIn(userIds).stream()
                .map(DeviceTokenDocument::getToken)
                .distinct()
                .toList();
    }
}
