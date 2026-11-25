package com.heuristiq.identity.role.repository;

import com.heuristiq.identity.role.UamRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface UamRoleRepository {
    UamRole save(UamRole role);

    Optional<UamRole> findById(String id);

    List<UamRole> findAll(Sort sort);

    Page<UamRole> findAll(Pageable pageable);

    void delete(UamRole role);

    Optional<UamRole> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<UamRole> search(String code, String name, Pageable pageable);

    /**
     * Roles an organization may use: the shared ones (no organization) plus the organization's own.
     *
     * @param tenantScope the organization; null means no restriction
     */
    List<UamRole> findVisible(String tenantScope, Sort sort);

    /** Paged, filtered form of {@link #findVisible}; code is an exact match, name a case-insensitive substring. */
    Page<UamRole> searchVisible(String tenantScope, String code, String name, Pageable pageable);
}
