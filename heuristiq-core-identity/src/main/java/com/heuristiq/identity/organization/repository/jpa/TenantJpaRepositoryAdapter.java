package com.heuristiq.uam.organization.repository.jpa;

import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.enums.UamEnums.TenantType;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.persistence.jpa.TenantEntity;
import com.heuristiq.uam.organization.repository.TenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class TenantJpaRepositoryAdapter implements TenantRepository {
    private final TenantJpaSpringRepository springRepository;

    TenantJpaRepositoryAdapter(TenantJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Tenant save(Tenant tenant) {
        return toDomain(springRepository.save(toEntity(tenant)));
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Tenant> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Tenant tenant) {
        springRepository.deleteById(tenant.getId());
    }

    @Override
    public Optional<Tenant> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    @Override
    public Page<Tenant> search(String code, String name, TenantType type, TenantStatus status, Pageable pageable) {
        return springRepository.search(code, name, type, status, pageable).map(this::toDomain);
    }

    private Tenant toDomain(TenantEntity entity) {
        return Tenant.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .legalName(entity.getLegalName())
                .description(entity.getDescription())
                .type(entity.getType())
                .logo(entity.getLogo())
                .website(entity.getWebsite())
                .emailDomain(entity.getEmailDomain())
                .timeZone(entity.getTimeZone())
                .defaultLanguage(entity.getDefaultLanguage())
                .country(entity.getCountry())
                .currency(entity.getCurrency())
                .supportEmail(entity.getSupportEmail())
                .supportPhone(entity.getSupportPhone())
                .status(entity.getStatus())
                .maxUsers(entity.getMaxUsers())
                .maxApplications(entity.getMaxApplications())
                .active(entity.getActive())
                .systemTenant(entity.getSystemTenant())
                .settings(entity.getSettings())
                .audit(entity.getAudit())
                .build();
    }

    private TenantEntity toEntity(Tenant tenant) {
        return TenantEntity.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .legalName(tenant.getLegalName())
                .description(tenant.getDescription())
                .type(tenant.getType())
                .logo(tenant.getLogo())
                .website(tenant.getWebsite())
                .emailDomain(tenant.getEmailDomain())
                .timeZone(tenant.getTimeZone())
                .defaultLanguage(tenant.getDefaultLanguage())
                .country(tenant.getCountry())
                .currency(tenant.getCurrency())
                .supportEmail(tenant.getSupportEmail())
                .supportPhone(tenant.getSupportPhone())
                .status(tenant.getStatus())
                .maxUsers(tenant.getMaxUsers())
                .maxApplications(tenant.getMaxApplications())
                .active(tenant.getActive())
                .systemTenant(tenant.getSystemTenant())
                .settings(tenant.getSettings())
                .audit(tenant.getAudit())
                .build();
    }
}
