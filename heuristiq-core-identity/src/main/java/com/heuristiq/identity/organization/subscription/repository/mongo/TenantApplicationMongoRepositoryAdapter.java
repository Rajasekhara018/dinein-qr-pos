package com.heuristiq.uam.organization.subscription.repository.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.persistence.mongo.TenantApplicationDocument;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class TenantApplicationMongoRepositoryAdapter implements TenantApplicationRepository {
    private final TenantApplicationMongoSpringRepository springRepository;

    TenantApplicationMongoRepositoryAdapter(TenantApplicationMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public TenantApplication save(TenantApplication subscription) {
        return toDomain(springRepository.save(toStored(subscription)));
    }

    @Override
    public Optional<TenantApplication> findByTenantIdAndApplicationCode(String tenantId, String applicationCode) {
        return springRepository.findByTenantIdAndApplicationCode(tenantId, applicationCode).map(this::toDomain);
    }

    @Override
    public List<TenantApplication> findByTenantId(String tenantId) {
        return springRepository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TenantApplication> findByApplicationCode(String applicationCode) {
        return springRepository.findByApplicationCode(applicationCode).stream().map(this::toDomain).toList();
    }

    private TenantApplicationDocument toStored(TenantApplication subscription) {
        return TenantApplicationDocument.builder()
                .id(subscription.getId())
                .tenantId(subscription.getTenantId())
                .applicationCode(subscription.getApplicationCode())
                .status(subscription.getStatus())
                .maxUsers(subscription.getMaxUsers())
                .validUntil(subscription.getValidUntil())
                .note(subscription.getNote())
                .subscribedBy(subscription.getSubscribedBy())
                .subscribedAt(subscription.getSubscribedAt())
                .updatedBy(subscription.getUpdatedBy())
                .updatedAt(subscription.getUpdatedAt())
                .ownerTeamUserIds(subscription.getOwnerTeamUserIds())
                .goLiveOverrideBy(subscription.getGoLiveOverrideBy())
                .goLiveOverrideAt(subscription.getGoLiveOverrideAt())
                .goLiveOverrideReason(subscription.getGoLiveOverrideReason())
                .goLiveOverrideSteps(subscription.getGoLiveOverrideSteps())
                .lastBlockedAlertAt(subscription.getLastBlockedAlertAt())
                .lastBlockedAlertFingerprint(subscription.getLastBlockedAlertFingerprint())
                .lastRefusedAlertAt(subscription.getLastRefusedAlertAt())
                .lastRefusedAlertFingerprint(subscription.getLastRefusedAlertFingerprint())
                .build();
    }

    private TenantApplication toDomain(TenantApplicationDocument stored) {
        return TenantApplication.builder()
                .id(stored.getId())
                .tenantId(stored.getTenantId())
                .applicationCode(stored.getApplicationCode())
                .status(stored.getStatus())
                .maxUsers(stored.getMaxUsers())
                .validUntil(stored.getValidUntil())
                .note(stored.getNote())
                .subscribedBy(stored.getSubscribedBy())
                .subscribedAt(stored.getSubscribedAt())
                .updatedBy(stored.getUpdatedBy())
                .updatedAt(stored.getUpdatedAt())
                .ownerTeamUserIds(stored.getOwnerTeamUserIds())
                .goLiveOverrideBy(stored.getGoLiveOverrideBy())
                .goLiveOverrideAt(stored.getGoLiveOverrideAt())
                .goLiveOverrideReason(stored.getGoLiveOverrideReason())
                .goLiveOverrideSteps(stored.getGoLiveOverrideSteps())
                .lastBlockedAlertAt(stored.getLastBlockedAlertAt())
                .lastBlockedAlertFingerprint(stored.getLastBlockedAlertFingerprint())
                .lastRefusedAlertAt(stored.getLastRefusedAlertAt())
                .lastRefusedAlertFingerprint(stored.getLastRefusedAlertFingerprint())
                .build();
    }
}
