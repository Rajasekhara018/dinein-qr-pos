package com.heuristiq.uam.userpreference.repository.jpa;

import com.heuristiq.uam.userpreference.persistance.jpa.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserPreferenceJpaSpringRepository extends JpaRepository<UserPreferenceEntity, String> {
    Optional<UserPreferenceEntity> findByUserIdAndApplicationId(String userId, String applicationId);
}
