package com.heuristq.dinein.kiosk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KioskDeviceRepository extends JpaRepository<KioskDeviceEntity, Long> {

    Optional<KioskDeviceEntity> findByTokenHash(String tokenHash);

    Optional<KioskDeviceEntity> findByPairingCodeHash(String pairingCodeHash);

    boolean existsByPairingCodeHash(String pairingCodeHash);

    Optional<KioskDeviceEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<KioskDeviceEntity> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
