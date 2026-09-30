package com.heuristq.dinein.kiosk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KioskUpsellRuleRepository extends JpaRepository<KioskUpsellRuleEntity, Long> {

    List<KioskUpsellRuleEntity> findAllByRestaurantIdOrderBySortOrderAscIdAsc(Long restaurantId);

    List<KioskUpsellRuleEntity> findAllByRestaurantIdAndActiveTrueOrderBySortOrderAscIdAsc(Long restaurantId);

    Optional<KioskUpsellRuleEntity> findByIdAndRestaurantId(Long id, Long restaurantId);
}
