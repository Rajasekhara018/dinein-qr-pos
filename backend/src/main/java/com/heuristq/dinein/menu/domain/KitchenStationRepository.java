package com.heuristq.dinein.menu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KitchenStationRepository extends JpaRepository<KitchenStationEntity, Long> {

    List<KitchenStationEntity> findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(Long restaurantId);

    Optional<KitchenStationEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    @Query("select coalesce(max(s.displayOrder), 0) from KitchenStationEntity s where s.restaurantId = :restaurantId")
    int maxDisplayOrder(@Param("restaurantId") Long restaurantId);
}
