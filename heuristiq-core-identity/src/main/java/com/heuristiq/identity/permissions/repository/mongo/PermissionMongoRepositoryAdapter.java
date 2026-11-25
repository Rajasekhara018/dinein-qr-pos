package com.heuristiq.identity.permissions.repository.mongo;

import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.permissions.persistance.mongo.PermissionDocument;
import com.heuristiq.identity.permissions.PermissionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class PermissionMongoRepositoryAdapter implements PermissionRepository {
    private final PermissionMongoSpringRepository springRepository;

    PermissionMongoRepositoryAdapter(PermissionMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public Permission save(Permission permission) {
        return toDomain(springRepository.save(toDocument(permission)));
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

    private Permission toDomain(PermissionDocument doc) {
        return Permission.builder()
                .id(doc.getId())
                .code(doc.getCode())
                .applicationCode(doc.getApplicationCode())
                .module(doc.getModule())
                .resource(doc.getResource())
                .action(doc.getAction())
                .displayName(doc.getDisplayName())
                .description(doc.getDescription())
                .active(doc.getActive())
                .systemPermission(doc.getSystemPermission())
                .attributes(doc.getAttributes())
                .audit(doc.getAudit())
                .build();
    }

    private PermissionDocument toDocument(Permission permission) {
        return PermissionDocument.builder()
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
