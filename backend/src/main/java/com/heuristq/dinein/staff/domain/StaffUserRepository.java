package com.heuristq.dinein.staff.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUserEntity, Long> {

    Optional<StaffUserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByRole(StaffRole role);

    List<StaffUserEntity> findAllByOrderByUsernameAsc();
}
