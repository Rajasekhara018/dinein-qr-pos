package com.heuristq.dinein.staff.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, Long> {

    Optional<StaffUserEntity> findByUsernameIgnoreCase(String username);

    Optional<StaffUserEntity> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** Username stays globally unique: login isn't restaurant-aware yet (see AuthService). */
    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRestaurantIdAndRole(Long restaurantId, StaffRole role);

    /** Platform-wide: used only by BootstrapOwnerRunner to decide whether the very first owner still needs creating. */
    boolean existsByRole(StaffRole role);

    List<StaffUserEntity> findByRoleOrderByUsernameAsc(StaffRole role);

    List<StaffUserEntity> findAllByRestaurantIdOrderByUsernameAsc(Long restaurantId);
}
