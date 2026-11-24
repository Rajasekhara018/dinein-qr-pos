package com.railcab.dms.documenttype.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data access to {@code dms_document_types}; every finder carries the organization. */
interface DocumentTypeJpaRepository extends JpaRepository<DocumentTypeJpaEntity, String> {

    Optional<DocumentTypeJpaEntity> findByTenantIdAndId(String tenantId, String id);

    Optional<DocumentTypeJpaEntity> findByTenantIdAndCode(String tenantId, String code);
}
