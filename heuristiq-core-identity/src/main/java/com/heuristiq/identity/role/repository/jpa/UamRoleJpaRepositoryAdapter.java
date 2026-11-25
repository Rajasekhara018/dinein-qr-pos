package com.heuristiq.identity.role.repository.jpa;

import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.persistance.jpa.UamRoleEntity;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UamRoleJpaRepositoryAdapter implements UamRoleRepository {
    private final UamRoleJpaSpringRepository springRepository;

    UamRoleJpaRepositoryAdapter(UamRoleJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UamRole save(UamRole role) {
        return toDomain(springRepository.save(toEntity(role)));
    }

    @Override
    public Optional<UamRole> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UamRole> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UamRole> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(UamRole role) {
        springRepository.deleteById(role.getId());
    }

    @Override
    public Optional<UamRole> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    @Override
    public Page<UamRole> search(String code, String name, Pageable pageable) {
        return springRepository.search(emptyIfBlank(code), emptyIfBlank(name), pageable).map(this::toDomain);
    }

    @Override
    public List<UamRole> findVisible(String tenantScope, Sort sort) {
        return springRepository.findVisible(emptyIfBlank(tenantScope), sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UamRole> searchVisible(String tenantScope, String code, String name, Pageable pageable) {
        return springRepository.searchVisible(emptyIfBlank(tenantScope), emptyIfBlank(code), emptyIfBlank(name), pageable).map(this::toDomain);
    }

    /** "No filter" is an empty string, not null: see UamRoleJpaSpringRepository. */
    private static String emptyIfBlank(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private UamRole toDomain(UamRoleEntity entity) {
        return UamRole.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.getActive())
                .systemRole(entity.getSystemRole())
                .editable(entity.getEditable())
                .permissions(entity.getPermissions())
                .assignedBy(entity.getAssignedBy())
                .assignmentReason(entity.getAssignmentReason())
                .assignmentType(entity.getAssignmentType())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveUntil(entity.getEffectiveUntil())
                .defaultRole(entity.getDefaultRole())
                .attributes(entity.getAttributes())
                .audit(entity.getAudit())
                .build();
    }

    private UamRoleEntity toEntity(UamRole role) {
        return UamRoleEntity.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .active(role.getActive())
                .systemRole(role.getSystemRole())
                .editable(role.getEditable())
                .permissions(role.getPermissions())
                .assignedBy(role.getAssignedBy())
                .assignmentReason(role.getAssignmentReason())
                .assignmentType(role.getAssignmentType())
                .effectiveFrom(role.getEffectiveFrom())
                .effectiveUntil(role.getEffectiveUntil())
                .defaultRole(role.getDefaultRole())
                .attributes(role.getAttributes())
                .audit(role.getAudit())
                .build();
    }
}
