package com.heuristiq.identity.securitypolicy.repository;

import com.heuristiq.identity.securitypolicy.SecurityPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SecurityPolicyRepository {
    SecurityPolicy save(SecurityPolicy securityPolicy);

    Optional<SecurityPolicy> findById(String id);

    List<SecurityPolicy> findAll();

    Page<SecurityPolicy> findAll(Pageable pageable);

    void delete(SecurityPolicy securityPolicy);

    Optional<SecurityPolicy> findByTenantIdAndApplicationId(String tenantId, String applicationId);

    /** Null-safe filtered + paged query (name = case-insensitive partial match, tenantId = exact
     *  match); a null/blank parameter imposes no constraint. */
    Page<SecurityPolicy> search(String name, String tenantId, Pageable pageable);
}
