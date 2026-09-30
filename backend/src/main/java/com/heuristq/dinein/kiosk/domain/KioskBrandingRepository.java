package com.heuristq.dinein.kiosk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KioskBrandingRepository extends JpaRepository<KioskBrandingEntity, Long> {

    Optional<KioskBrandingEntity> findByRestaurantId(Long restaurantId);
}
