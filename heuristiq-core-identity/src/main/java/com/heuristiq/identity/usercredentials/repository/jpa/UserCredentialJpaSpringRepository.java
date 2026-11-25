package com.heuristiq.identity.usercredentials.repository.jpa;

import com.heuristiq.identity.usercredentials.persistance.jpa.UserCredentialEntity;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserCredentialJpaSpringRepository extends JpaRepository<UserCredentialEntity, String> {
    List<UserCredentialEntity> findByUserId(String userId);

    Optional<UserCredentialEntity> findByUserIdAndProvider(String userId, AuthProvider provider);

    Optional<UserCredentialEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
