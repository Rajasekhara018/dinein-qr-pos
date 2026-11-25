package com.heuristiq.identity.application.client.repository;

import com.heuristiq.identity.application.client.ApiClient;
import com.heuristiq.identity.enums.UamEnums.ApiClientType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface ApiClientRepository {
    ApiClient save(ApiClient client);

    Optional<ApiClient> findById(String id);

    List<ApiClient> findAll(Sort sort);

    Page<ApiClient> findAll(Pageable pageable);

    void delete(ApiClient client);

    Optional<ApiClient> findByClientIdIgnoreCase(String clientId);

    boolean existsByClientIdIgnoreCase(String clientId);

    /** Null-safe filtered + paged query (clientId, clientType and active are all exact matches);
     *  a null parameter imposes no constraint. */
    Page<ApiClient> search(String clientId, ApiClientType clientType, Boolean active, Pageable pageable);
}
