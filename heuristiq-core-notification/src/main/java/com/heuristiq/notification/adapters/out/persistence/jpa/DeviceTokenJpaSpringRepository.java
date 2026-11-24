package com.railcab.nms.adapters.out.persistence.jpa;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railcab.nms.adapters.out.persistence.jpa.DeviceTokenEntity;

interface DeviceTokenJpaSpringRepository extends JpaRepository<DeviceTokenEntity, String> {

    boolean existsByUserIdAndToken(String userId, String token);

    void deleteByUserIdAndToken(String userId, String token);

    void deleteByToken(String token);

    List<DeviceTokenEntity> findByUserId(String userId);

    /** Drops the same token registered under any OTHER user (see IDeviceTokenRepository#register). */
    void deleteByTokenAndUserIdNot(String token, String userId);

    Optional<DeviceTokenEntity> findFirstByUserIdAndToken(String userId, String token);

    List<DeviceTokenEntity> findByUserIdIn(Collection<String> userIds);

    @Query("SELECT e FROM DeviceTokenEntity e WHERE e.userId IN :userIds AND (e.tenantId IS NULL OR e.tenantId = :tenantId)")
    List<DeviceTokenEntity> findByUserIdInScoped(@Param("userIds") Collection<String> userIds, @Param("tenantId") String tenantId);
}
