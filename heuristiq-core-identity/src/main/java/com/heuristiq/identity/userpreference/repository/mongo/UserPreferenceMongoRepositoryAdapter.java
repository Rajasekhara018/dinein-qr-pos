package com.heuristiq.identity.userpreference.repository.mongo;

import com.heuristiq.identity.userpreference.UserPreference;
import com.heuristiq.identity.userpreference.persistance.mongo.UserPreferenceDocument;
import com.heuristiq.identity.userpreference.UserPreferenceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserPreferenceMongoRepositoryAdapter implements UserPreferenceRepository {
    private final UserPreferenceMongoSpringRepository springRepository;

    UserPreferenceMongoRepositoryAdapter(UserPreferenceMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserPreference save(UserPreference preference) {
        return toDomain(springRepository.save(toDocument(preference)));
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

    private UserPreference toDomain(UserPreferenceDocument doc) {
        return UserPreference.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .userId(doc.getUserId())
                .applicationId(doc.getApplicationId())
                .ui(doc.getUi())
                .notification(doc.getNotification())
                .dashboard(doc.getDashboard())
                .accessibility(doc.getAccessibility())
                .regional(doc.getRegional())
                .active(doc.getActive())
                .version(doc.getVersion())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private UserPreferenceDocument toDocument(UserPreference preference) {
        return UserPreferenceDocument.builder()
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
