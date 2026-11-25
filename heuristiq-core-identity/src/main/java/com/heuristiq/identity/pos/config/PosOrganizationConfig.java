package com.heuristiq.identity.pos.config;

import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.pos.adapter.out.AuditLogPosAudit;
import com.heuristiq.identity.pos.adapter.out.JdbcBranchStaffStore;
import com.heuristiq.identity.pos.adapter.out.JdbcBranchStore;
import com.heuristiq.identity.pos.adapter.out.JdbcLegalEntityStore;
import com.heuristiq.identity.pos.adapter.out.TenantPosOrganizationDirectory;
import com.heuristiq.identity.pos.application.PosOrganizationService;
import com.heuristiq.identity.pos.application.PosOrganizationUseCase;
import com.heuristiq.identity.pos.application.port.BranchStaffStore;
import com.heuristiq.identity.pos.application.port.BranchStore;
import com.heuristiq.identity.pos.application.port.LegalEntityStore;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Wiring of the POS organization model: PostgreSQL stores and the use case on the UTC clock, all only with the postgres persistence type (POS has no Mongo store). */
@Configuration
public class PosOrganizationConfig {

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    LegalEntityStore jdbcLegalEntityStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new JdbcLegalEntityStore(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    BranchStore jdbcBranchStore(JdbcTemplate jdbc) {
        return new JdbcBranchStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    BranchStaffStore jdbcBranchStaffStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new JdbcBranchStaffStore(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    PosOrganizationUseCase posOrganizationUseCase(TenantRepository tenants, TenantMembershipRepository memberships,
            LegalEntityStore legalEntities, BranchStore branches, BranchStaffStore staff, AuditLogServiceUseCase auditLogs) {
        return new PosOrganizationService(new TenantPosOrganizationDirectory(tenants, memberships), legalEntities, branches, staff,
                new AuditLogPosAudit(auditLogs), Clock.systemUTC());
    }
}
