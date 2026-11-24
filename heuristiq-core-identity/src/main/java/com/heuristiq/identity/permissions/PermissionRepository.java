package com.heuristiq.uam.permissions;

import com.heuristiq.uam.permissions.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository {
    Permission save(Permission permission);

    Optional<Permission> findById(String id);

    List<Permission> findAll(Sort sort);

    Page<Permission> findAll(Pageable pageable);

    void delete(Permission permission);

    Optional<Permission> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** The permissions whose code is exactly one of {@code codes} (unknown codes are skipped). */
    List<Permission> findAllByCodeIn(Collection<String> codes);
}
