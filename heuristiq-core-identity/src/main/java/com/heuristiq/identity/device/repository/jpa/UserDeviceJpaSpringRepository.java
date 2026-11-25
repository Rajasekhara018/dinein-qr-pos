package com.heuristiq.identity.device.repository.jpa;

import com.heuristiq.identity.device.persistence.jpa.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserDeviceJpaSpringRepository extends JpaRepository<UserDeviceEntity, String> {
    List<UserDeviceEntity> findByUserId(String userId);

    Optional<UserDeviceEntity> findByUserIdAndDeviceFingerprint(String userId, String deviceFingerprint);
}
