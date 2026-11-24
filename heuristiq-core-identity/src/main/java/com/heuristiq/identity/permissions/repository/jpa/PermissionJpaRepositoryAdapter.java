package com.heuristiq.uam.permissions.repository.jpa;

import com.heuristiq.uam.permissions.Permission;
import com.heuristiq.uam.permissions.persistance.jpa.PermissionEntity;
import com.heuristiq.uam.permissions.PermissionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class PermissionJpaRepositoryAdapter implements PermissionRepository {
    private final PermissionJpaSpringRepository springRepository;

    PermissionJpaRepositoryAdapter(PermissionJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Permission save(Permission permission) {
        return toDomain(springRepository.save(toEntity(permission)));
    }

    @Override
    public Optional<Permission> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Permission> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Permission> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Permission permission) {
        springRepository.deleteById(permission.getId());
    }

    @Override
    public Optional<Permission> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public List<Permission> findAllByCodeIn(Collection<String> codes) {
        return codes == null || codes.isEmpty()
                ? List.of()
                : springRepository.findByCodeIn(codes).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    private Permission toDomain(PermissionEntity entity) {
        return Permission.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .applicationCode(entity.getApplicationCode())
                .module(entity.getModule())
                .resource(entity.getResource())
                .action(entity.getAction())
                .displayName(entity.getDisplayName())
                .description(entity.getDescription())
                .active(entity.getActive())
                .systemPermission(entity.getSystemPermission())
                .attributes(entity.getAttributes())
                .audit(entity.getAudit())
                .build();
    }

    private PermissionEntity toEntity(Permission permission) {
        return PermissionEntity.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .applicationCode(permission.getApplicationCode())
                .module(permission.getModule())
                .resource(permission.getResource())
                .action(permission.getAction())
                .displayName(permission.getDisplayName())
                .description(permission.getDescription())
                .active(permission.getActive())
                .systemPermission(permission.getSystemPermission())
                .attributes(permission.getAttributes())
                .audit(permission.getAudit())
                .build();
    }
}
