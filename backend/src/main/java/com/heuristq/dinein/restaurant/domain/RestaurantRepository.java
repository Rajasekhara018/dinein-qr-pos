package com.heuristq.dinein.restaurant.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<RestaurantEntity, Long> {

    Optional<RestaurantEntity> findBySlugIgnoreCase(String slug);
}
