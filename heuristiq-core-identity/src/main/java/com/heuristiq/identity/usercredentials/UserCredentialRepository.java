package com.heuristiq.uam.usercredentials;

import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;

import java.util.List;
import java.util.Optional;

public interface UserCredentialRepository {
    UserCredential save(UserCredential credential);

    Optional<UserCredential> findById(String id);

    List<UserCredential> findByUserId(String userId);

    Optional<UserCredential> findByUserIdAndProvider(String userId, AuthProvider provider);

    Optional<UserCredential> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
