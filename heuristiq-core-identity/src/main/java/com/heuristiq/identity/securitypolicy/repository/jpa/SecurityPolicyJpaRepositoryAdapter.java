package com.heuristiq.identity.securitypolicy.repository.jpa;

import com.heuristiq.identity.securitypolicy.SecurityPolicy;
import com.heuristiq.identity.securitypolicy.persistence.jpa.SecurityPolicyEntity;
import com.heuristiq.identity.securitypolicy.repository.SecurityPolicyRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class SecurityPolicyJpaRepositoryAdapter implements SecurityPolicyRepository {
    private final SecurityPolicyJpaSpringRepository springRepository;

    SecurityPolicyJpaRepositoryAdapter(SecurityPolicyJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public SecurityPolicy save(SecurityPolicy securityPolicy) {
        return toDomain(springRepository.save(toEntity(securityPolicy)));
    }

    @Override
    public Optional<SecurityPolicy> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SecurityPolicy> findAll() {
        return springRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Page<SecurityPolicy> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(SecurityPolicy securityPolicy) {
        springRepository.deleteById(securityPolicy.getId());
    }

    @Override
    public Optional<SecurityPolicy> findByTenantIdAndApplicationId(String tenantId, String applicationId) {
        return springRepository.findByTenantIdAndApplicationId(tenantId, applicationId).map(this::toDomain);
    }

    @Override
    public Page<SecurityPolicy> search(String name, String tenantId, Pageable pageable) {
        return springRepository.search(name, tenantId, pageable).map(this::toDomain);
    }

    private SecurityPolicy toDomain(SecurityPolicyEntity entity) {
        return SecurityPolicy.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .tenantId(entity.getTenantId())
                .applicationId(entity.getApplicationId())
                .name(entity.getName())
                .description(entity.getDescription())
                .passwordPolicy(entity.getPasswordPolicy())
                .loginPolicy(entity.getLoginPolicy())
                .sessionPolicy(entity.getSessionPolicy())
                .mfaPolicy(entity.getMfaPolicy())
                .apiSecurityPolicy(entity.getApiSecurityPolicy())
                .accountPolicy(entity.getAccountPolicy())
                .active(entity.getActive())
                .systemPolicy(entity.getSystemPolicy())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveUntil(entity.getEffectiveUntil())
                .audit(entity.getAudit())
                .build();
    }

    private SecurityPolicyEntity toEntity(SecurityPolicy securityPolicy) {
        return SecurityPolicyEntity.builder()
                .id(securityPolicy.getId())
                .version(securityPolicy.getVersion())
                .tenantId(securityPolicy.getTenantId())
                .applicationId(securityPolicy.getApplicationId())
                .name(securityPolicy.getName())
                .description(securityPolicy.getDescription())
                .passwordPolicy(securityPolicy.getPasswordPolicy())
                .loginPolicy(securityPolicy.getLoginPolicy())
                .sessionPolicy(securityPolicy.getSessionPolicy())
                .mfaPolicy(securityPolicy.getMfaPolicy())
                .apiSecurityPolicy(securityPolicy.getApiSecurityPolicy())
                .accountPolicy(securityPolicy.getAccountPolicy())
                .active(securityPolicy.getActive())
                .systemPolicy(securityPolicy.getSystemPolicy())
                .effectiveFrom(securityPolicy.getEffectiveFrom())
                .effectiveUntil(securityPolicy.getEffectiveUntil())
                .audit(securityPolicy.getAudit())
                .build();
    }
}
