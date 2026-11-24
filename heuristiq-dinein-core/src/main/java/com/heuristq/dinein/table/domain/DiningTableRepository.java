package com.heuristq.dinein.table.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiningTableRepository extends JpaRepository<DiningTableEntity, Long> {

    Optional<DiningTableEntity> findByQrToken(String qrToken);

    Optional<DiningTableEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndLabelIgnoreCase(Long restaurantId, String label);

    boolean existsByRestaurantIdAndLabelIgnoreCaseAndIdNot(Long restaurantId, String label, Long id);

    List<DiningTableEntity> findAllByRestaurantIdOrderByLabelAsc(Long restaurantId);
}
