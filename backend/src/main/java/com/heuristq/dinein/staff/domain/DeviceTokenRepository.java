package com.heuristq.dinein.staff.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, Long> {

    Optional<DeviceTokenEntity> findByTokenHash(String tokenHash);

    Optional<DeviceTokenEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<DeviceTokenEntity> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<DeviceTokenEntity> findByStaffUserIdAndRevokedAtIsNull(Long staffUserId);
}
