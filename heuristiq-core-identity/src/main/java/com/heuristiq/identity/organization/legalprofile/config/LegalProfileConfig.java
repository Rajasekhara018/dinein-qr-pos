package com.heuristiq.uam.organization.legalprofile.config;

import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.organization.legalprofile.adapter.out.AuditLogLegalProfileAudit;
import com.heuristiq.uam.organization.legalprofile.adapter.out.JdbcLegalProfileStore;
import com.heuristiq.uam.organization.legalprofile.adapter.out.MongoLegalProfileStore;
import com.heuristiq.uam.organization.legalprofile.adapter.out.TenantLegalOrganizationDirectory;
import com.heuristiq.uam.organization.legalprofile.application.LegalProfileService;
import com.heuristiq.uam.organization.legalprofile.application.LegalProfileUseCase;
import com.heuristiq.uam.organization.legalprofile.application.port.LegalProfileStore;
import com.heuristiq.uam.organization.repository.TenantRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wiring of the organization legal profile. The profile is kept in Postgres tables or a Mongo collection depending on the
 * persistence type; the legal name stays on the tenant record. Uses the UTC system clock (UAM has no shared Clock bean).
 */
@Configuration
public class LegalProfileConfig {

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    LegalProfileStore jdbcLegalProfileStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new JdbcLegalProfileStore(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    LegalProfileStore mongoLegalProfileStore(MongoTemplate mongo) {
        return new MongoLegalProfileStore(mongo);
    }

    @Bean
    LegalProfileUseCase legalProfileUseCase(TenantRepository tenants, LegalProfileStore store, AuditLogServiceUseCase auditLogs) {
        return new LegalProfileService(new TenantLegalOrganizationDirectory(tenants), store, new AuditLogLegalProfileAudit(auditLogs),
                Clock.systemUTC());
    }
}
