package com.heuristiq.identity.userpreference;

import com.heuristiq.identity.userpreference.UserPreference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceRepository {
    UserPreference save(UserPreference preference);

    Optional<UserPreference> findById(String id);

    List<UserPreference> findAll();

    Page<UserPreference> findAll(Pageable pageable);

    void delete(UserPreference preference);

    Optional<UserPreference> findByUserIdAndApplicationId(String userId, String applicationId);
}
