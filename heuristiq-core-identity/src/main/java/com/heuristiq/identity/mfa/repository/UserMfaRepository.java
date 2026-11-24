package com.heuristiq.uam.mfa.repository;

import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.enums.UamEnums.MfaType;

import java.util.List;
import java.util.Optional;

public interface UserMfaRepository {
    UserMfa save(UserMfa mfa);

    Optional<UserMfa> findById(String id);

    List<UserMfa> findByUserId(String userId);

    Optional<UserMfa> findByUserIdAndMfaType(String userId, MfaType mfaType);

    void delete(UserMfa mfa);
}
