package com.heuristiq.identity.organization.repository;

import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.organization.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface TenantRepository {
    Tenant save(Tenant tenant);

    Optional<Tenant> findById(String id);

    List<Tenant> findAll(Sort sort);

    Page<Tenant> findAll(Pageable pageable);

    void delete(Tenant tenant);

    Optional<Tenant> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Null-safe filtered + paged query (code = exact match, name = case-insensitive partial match,
     *  type/status = exact match); a null/blank parameter imposes no constraint. */
    Page<Tenant> search(String code, String name, TenantType type, TenantStatus status, Pageable pageable);
}
