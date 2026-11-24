package com.heuristiq.uam.device.repository.jpa;

import com.heuristiq.uam.device.persistence.jpa.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserDeviceJpaSpringRepository extends JpaRepository<UserDeviceEntity, String> {
    List<UserDeviceEntity> findByUserId(String userId);

    Optional<UserDeviceEntity> findByUserIdAndDeviceFingerprint(String userId, String deviceFingerprint);
}
