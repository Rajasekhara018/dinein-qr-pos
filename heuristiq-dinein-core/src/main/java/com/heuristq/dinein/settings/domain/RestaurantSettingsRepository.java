package com.heuristq.dinein.settings.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettingsEntity, Long> {

    Optional<RestaurantSettingsEntity> findByRestaurantId(Long restaurantId);

    boolean existsByRestaurantId(Long restaurantId);
}
