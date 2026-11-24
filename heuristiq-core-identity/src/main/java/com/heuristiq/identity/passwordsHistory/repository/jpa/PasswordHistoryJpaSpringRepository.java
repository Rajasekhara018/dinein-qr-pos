package com.heuristiq.uam.passwordsHistory.repository.jpa;

import com.heuristiq.uam.passwordsHistory.persistance.jpa.PasswordHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordHistoryJpaSpringRepository extends JpaRepository<PasswordHistoryEntity, String> {
    Page<PasswordHistoryEntity> findByUserId(String userId, Pageable pageable);
}
