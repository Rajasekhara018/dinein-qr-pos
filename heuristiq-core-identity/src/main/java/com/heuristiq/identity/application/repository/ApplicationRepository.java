package com.heuristiq.identity.application.repository;

import com.heuristiq.identity.application.Application;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {
    Application save(Application application);

    Optional<Application> findById(String id);

    List<Application> findAll(Sort sort);

    Page<Application> findAll(Pageable pageable);

    void delete(Application application);

    Optional<Application> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Null-safe filtered + paged query (code = exact match, name = case-insensitive partial match,
     *  type = exact match); a null/blank parameter imposes no constraint. */
    Page<Application> search(String code, String name, ApplicationType type, Pageable pageable);
}
