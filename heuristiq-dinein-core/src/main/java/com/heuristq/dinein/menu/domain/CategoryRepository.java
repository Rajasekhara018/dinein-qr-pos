package com.heuristq.dinein.menu.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(Long restaurantId);

    Optional<CategoryEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<CategoryEntity> findByActiveTrueAndRestaurantIdOrderByDisplayOrderAscNameAsc(Long restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    @Query("select coalesce(max(c.displayOrder), 0) from CategoryEntity c where c.restaurantId = :restaurantId")
    int maxDisplayOrder(@Param("restaurantId") Long restaurantId);
}
