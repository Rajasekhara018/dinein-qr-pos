package com.heuristiq.uam.userpreference.repository.jpa;

import com.heuristiq.uam.userpreference.UserPreference;
import com.heuristiq.uam.userpreference.persistance.jpa.UserPreferenceEntity;
import com.heuristiq.uam.userpreference.UserPreferenceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserPreferenceJpaRepositoryAdapter implements UserPreferenceRepository {
    private final UserPreferenceJpaSpringRepository springRepository;

    UserPreferenceJpaRepositoryAdapter(UserPreferenceJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserPreference save(UserPreference preference) {
        return toDomain(springRepository.save(toEntity(preference)));
    }

    @Override
    public Optional<UserPreference> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UserPreference> findAll() {
        return springRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UserPreference> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(UserPreference preference) {
        springRepository.deleteById(preference.getId());
    }

    @Override
    public Optional<UserPreference> findByUserIdAndApplicationId(String userId, String applicationId) {
        return springRepository.findByUserIdAndApplicationId(userId, applicationId).map(this::toDomain);
    }

    private UserPreference toDomain(UserPreferenceEntity entity) {
        return UserPreference.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .userId(entity.getUserId())
                .applicationId(entity.getApplicationId())
                .ui(entity.getUi())
                .notification(entity.getNotification())
                .dashboard(entity.getDashboard())
                .accessibility(entity.getAccessibility())
                .regional(entity.getRegional())
                .active(entity.getActive())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UserPreferenceEntity toEntity(UserPreference preference) {
        return UserPreferenceEntity.builder()
                .id(preference.getId())
                .tenantId(preference.getTenantId())
                .userId(preference.getUserId())
                .applicationId(preference.getApplicationId())
                .ui(preference.getUi())
                .notification(preference.getNotification())
                .dashboard(preference.getDashboard())
                .accessibility(preference.getAccessibility())
                .regional(preference.getRegional())
                .active(preference.getActive())
                .version(preference.getVersion())
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
