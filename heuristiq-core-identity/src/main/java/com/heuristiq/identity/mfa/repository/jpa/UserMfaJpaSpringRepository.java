package com.heuristiq.identity.mfa.repository.jpa;

import com.heuristiq.identity.mfa.persistence.jpa.UserMfaEntity;
import com.heuristiq.identity.enums.UamEnums.MfaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserMfaJpaSpringRepository extends JpaRepository<UserMfaEntity, String> {
    List<UserMfaEntity> findByUserId(String userId);

    Optional<UserMfaEntity> findByUserIdAndMfaType(String userId, MfaType mfaType);
}
