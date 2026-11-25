package com.heuristiq.identity.organization.operatingmodel.config;

import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.organization.operatingmodel.adapter.out.AuditLogOperatingModelAudit;
import com.heuristiq.identity.organization.operatingmodel.adapter.out.JdbcOperatingModelScheduleStore;
import com.heuristiq.identity.organization.operatingmodel.adapter.out.MongoOperatingModelScheduleStore;
import com.heuristiq.identity.organization.operatingmodel.adapter.out.TenantOrganizationDirectory;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelService;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.identity.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.identity.organization.repository.TenantRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wiring of the operating model. Uses the UTC system clock (UAM has no shared Clock bean); the day is taken in the
 * organization's zone. The schedule is a Postgres table or a Mongo collection depending on the persistence type.
 */
@Configuration
@EnableConfigurationProperties(OperatingModelProperties.class)
public class OperatingModelConfig {

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    OperatingModelScheduleStore jdbcOperatingModelScheduleStore(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new JdbcOperatingModelScheduleStore(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    OperatingModelScheduleStore mongoOperatingModelScheduleStore(MongoTemplate mongo) {
        return new MongoOperatingModelScheduleStore(mongo);
    }

    @Bean
    OperatingModelUseCase operatingModelUseCase(TenantRepository tenants, OperatingModelScheduleStore schedules,
                                                AuditLogServiceUseCase auditLogs, OperatingModelProperties properties) {
        return new OperatingModelService(new TenantOrganizationDirectory(tenants), schedules, new AuditLogOperatingModelAudit(auditLogs),
                Clock.systemUTC(), ZoneId.of(properties.defaultTimeZone()));
    }
}
