package com.railcab.dms.document.audit.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentAuditJpaRepository extends JpaRepository<DocumentAuditJpaEntity, String> {
}
