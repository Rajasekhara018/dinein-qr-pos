package com.heuristiq.uam.usercredentials.repository.jpa;

import com.heuristiq.uam.usercredentials.persistance.jpa.UserCredentialEntity;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserCredentialJpaSpringRepository extends JpaRepository<UserCredentialEntity, String> {
    List<UserCredentialEntity> findByUserId(String userId);

    Optional<UserCredentialEntity> findByUserIdAndProvider(String userId, AuthProvider provider);

    Optional<UserCredentialEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
