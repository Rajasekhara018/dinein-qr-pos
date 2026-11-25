package com.heuristiq.identity.application.repository.jpa;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.application.persistence.jpa.ApplicationEntity;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class ApplicationJpaRepositoryAdapter implements ApplicationRepository {
    private final ApplicationJpaSpringRepository springRepository;

    ApplicationJpaRepositoryAdapter(ApplicationJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Application save(Application application) {
        return toDomain(springRepository.save(toEntity(application)));
    }

    @Override
    public Optional<Application> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Application> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Application> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Application application) {
        springRepository.deleteById(application.getId());
    }

    @Override
    public Optional<Application> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    @Override
    public Page<Application> search(String code, String name, ApplicationType type, Pageable pageable) {
        return springRepository.search(code, name, type, pageable).map(this::toDomain);
    }

    private Application toDomain(ApplicationEntity entity) {
        return Application.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .appVersion(entity.getAppVersion())
                .baseUrl(entity.getBaseUrl())
                .icon(entity.getIcon())
                .landingPage(entity.getLandingPage())
                .ownerTeam(entity.getOwnerTeam())
                .supportEmail(entity.getSupportEmail())
                .type(entity.getType())
                .authenticationMode(entity.getAuthenticationMode())
                .ssoEnabled(entity.getSsoEnabled())
                .mfaRequired(entity.getMfaRequired())
                .displayOrder(entity.getDisplayOrder())
                .active(entity.getActive())
                .systemApplication(entity.getSystemApplication())
                .attributes(entity.getAttributes())
                .audit(entity.getAudit())
                .build();
    }

    private ApplicationEntity toEntity(Application application) {
        return ApplicationEntity.builder()
                .id(application.getId())
                .code(application.getCode())
                .name(application.getName())
                .description(application.getDescription())
                .appVersion(application.getAppVersion())
                .baseUrl(application.getBaseUrl())
                .icon(application.getIcon())
                .landingPage(application.getLandingPage())
                .ownerTeam(application.getOwnerTeam())
                .supportEmail(application.getSupportEmail())
                .type(application.getType())
                .authenticationMode(application.getAuthenticationMode())
                .ssoEnabled(application.getSsoEnabled())
                .mfaRequired(application.getMfaRequired())
                .displayOrder(application.getDisplayOrder())
                .active(application.getActive())
                .systemApplication(application.getSystemApplication())
                .attributes(application.getAttributes())
                .audit(application.getAudit())
                .build();
    }
}
