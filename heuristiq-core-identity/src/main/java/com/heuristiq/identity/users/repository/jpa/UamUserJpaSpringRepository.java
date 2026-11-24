package com.heuristiq.uam.users.repository.jpa;

import com.heuristiq.uam.users.persistence.jpa.UamUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface UamUserJpaSpringRepository extends JpaRepository<UamUserEntity, String>, JpaSpecificationExecutor<UamUserEntity> {
    Optional<UamUserEntity> findByUserNameIgnoreCase(String userName);

    Optional<UamUserEntity> findByEmailIgnoreCase(String email);

    Optional<UamUserEntity> findByPhoneE164(String phoneE164);

    List<UamUserEntity> findAllByPhoneNumber_Number(String number);

    boolean existsByUserNameIgnoreCase(String userName);
}
