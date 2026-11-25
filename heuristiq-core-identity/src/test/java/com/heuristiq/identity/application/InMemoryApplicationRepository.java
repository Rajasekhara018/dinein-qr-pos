package com.heuristiq.identity.application;

import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test double of the applications table: one row per code, ids assigned on first save. */
public final class InMemoryApplicationRepository implements ApplicationRepository {
    public final List<Application> rows = new ArrayList<>();

    @Override
    public Application save(Application application) {
        if (application.getId() == null) {
            application.setId(UUID.randomUUID().toString());
            rows.add(application);
        }
        return application;
    }

    @Override
    public Optional<Application> findById(String id) {
        return rows.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    @Override
    public List<Application> findAll(Sort sort) {
        return rows.stream().sorted(Comparator.comparing(Application::getCode)).toList();
    }

    @Override
    public Page<Application> findAll(Pageable pageable) {
        return new PageImpl<>(findAll(pageable.getSort()), pageable, rows.size());
    }

    @Override
    public void delete(Application application) {
        rows.removeIf(r -> r.getId().equals(application.getId()));
    }

    @Override
    public Optional<Application> findByCodeIgnoreCase(String code) {
        return rows.stream().filter(r -> r.getCode().equalsIgnoreCase(code)).findFirst();
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return findByCodeIgnoreCase(code).isPresent();
    }

    @Override
    public Page<Application> search(String code, String name, ApplicationType type, Pageable pageable) {
        throw new UnsupportedOperationException("the application service filters in memory");
    }
}
