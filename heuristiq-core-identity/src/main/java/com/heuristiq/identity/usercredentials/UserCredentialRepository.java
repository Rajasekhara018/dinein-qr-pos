package com.heuristiq.identity.usercredentials;

import com.heuristiq.identity.usercredentials.UserCredential;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;

import java.util.List;
import java.util.Optional;

public interface UserCredentialRepository {
    UserCredential save(UserCredential credential);

    Optional<UserCredential> findById(String id);

    List<UserCredential> findByUserId(String userId);

    Optional<UserCredential> findByUserIdAndProvider(String userId, AuthProvider provider);

    Optional<UserCredential> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
