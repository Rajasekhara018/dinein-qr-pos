package com.heuristiq.identity.readiness.config;

import java.time.Clock;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.heuristiq.identity.application.SessionOrganizationResolver;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.GoLiveGuard;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.orgstructure.application.OrgStructureUseCase;
import com.heuristiq.identity.readiness.adapter.out.AuditLogGovernanceAudit;
import com.heuristiq.identity.readiness.adapter.out.AuditLogReadinessSeverityAudit;
import com.heuristiq.identity.readiness.application.ReadinessSeverityService;
import com.heuristiq.identity.readiness.application.ReadinessSeverityUseCase;
import com.heuristiq.identity.readiness.application.port.ReadinessSeverityAudit;
import com.heuristiq.identity.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort;
import com.heuristiq.identity.readiness.adapter.out.JdbcSchedulerLease;
import com.heuristiq.identity.readiness.adapter.out.MongoSchedulerLease;
import com.heuristiq.identity.readiness.adapter.out.NoopReadinessAlertSender;
import com.heuristiq.identity.readiness.adapter.out.OrgStructureOwnerCandidateDirectory;
import com.heuristiq.identity.readiness.adapter.out.SecurityGovernanceCaller;
import com.heuristiq.identity.readiness.adapter.out.UamAlertRecipientDirectory;
import com.heuristiq.identity.readiness.application.ApplicationReadinessUseCase;
import com.heuristiq.identity.readiness.application.GoLiveGuardService;
import com.heuristiq.identity.readiness.application.ReadinessAlertService;
import com.heuristiq.identity.readiness.application.ReadinessGovernanceService;
import com.heuristiq.identity.readiness.application.ReadinessRecheckService;
import com.heuristiq.identity.readiness.application.port.AlertRecipientDirectory;
import com.heuristiq.identity.readiness.application.port.GovernanceAudit;
import com.heuristiq.identity.readiness.application.port.GovernanceCaller;
import com.heuristiq.identity.readiness.application.port.OwnerCandidateDirectory;
import com.heuristiq.identity.readiness.application.port.ReadinessAlertSender;
import com.heuristiq.identity.readiness.application.port.SchedulerLease;
import com.heuristiq.identity.users.repository.UamUserRepository;

/**
 * Wiring of the go-live guard, override, owner team, alerts and the scheduled re-check. Uses the UTC system clock (UAM has no
 * shared Clock bean). The lease is a Postgres row or a Mongo document depending on the persistence type.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(GovernanceProperties.class)
public class GovernanceConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GovernanceConfig.class);

    private final GovernanceProperties properties;
    private final ObjectProvider<ReadinessRecheckService> recheck;

    public GovernanceConfig(GovernanceProperties properties, ObjectProvider<ReadinessRecheckService> recheck) {
        this.properties = properties;
        this.recheck = recheck;
    }

    /** Registers the periodic re-check (fixed delay, so runs never overlap on one instance). Off with recheck-enabled=false. */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!properties.recheckEnabled()) {
            return;
        }
        registrar.addFixedDelayTask(new FixedDelayTask(this::runRecheck, properties.recheckInterval(), properties.recheckInitialDelay()));
    }

    private void runRecheck() {
        try {
            ReadinessRecheckService service = recheck.getIfAvailable();
            if (service != null) {
                service.runOnce();
            }
        } catch (RuntimeException e) {
            // A scheduler loop must survive: log with context, try again at the next interval.
            log.error("readiness.recheck.crashed reason={}", e.toString(), e);
        }
    }

    @Bean
    GovernanceCaller governanceCaller() {
        return new SecurityGovernanceCaller();
    }

    @Bean
    GovernanceAudit governanceAudit(AuditLogServiceUseCase auditLogs) {
        return new AuditLogGovernanceAudit(auditLogs);
    }

    @Bean
    ReadinessSeverityAudit readinessSeverityAudit(AuditLogServiceUseCase auditLogs) {
        return new AuditLogReadinessSeverityAudit(auditLogs);
    }

    @Bean
    ReadinessSeverityUseCase readinessSeverityUseCase(UamReadinessFactsPort facts, ReadinessSeverityOverrideRepository overrides,
                                                      ApplicationReadinessUseCase readiness, ReadinessSeverityAudit audit,
                                                      GovernanceCaller caller) {
        return new ReadinessSeverityService(facts, overrides, readiness, audit, caller, Clock.systemUTC());
    }

    @Bean
    AlertRecipientDirectory alertRecipientDirectory(UamUserRepository users, TenantMembershipRepository memberships) {
        return new UamAlertRecipientDirectory(users, memberships);
    }

    @Bean
    OwnerCandidateDirectory ownerCandidateDirectory(ObjectProvider<OrgStructureUseCase> structure) {
        return new OrgStructureOwnerCandidateDirectory(structure);
    }

    @Bean
    ReadinessAlertSender readinessAlertSender() {
        return new NoopReadinessAlertSender();
    }

    @Bean
    ReadinessAlertService readinessAlertService(TenantApplicationServiceUseCase links, AlertRecipientDirectory directory,
                                                ReadinessAlertSender sender) {
        return new ReadinessAlertService(links, directory, sender, properties, Clock.systemUTC());
    }

    @Bean
    GoLiveGuard goLiveGuard(ApplicationReadinessUseCase readiness, ReadinessAlertService alerts, GovernanceAudit audit,
                            GovernanceCaller caller) {
        return new GoLiveGuardService(readiness, alerts, audit, caller, properties);
    }

    @Bean
    ReadinessGovernanceService readinessGovernanceService(SessionOrganizationResolver session, TenantApplicationServiceUseCase links,
                                                          ApplicationReadinessUseCase readiness, OwnerCandidateDirectory candidates,
                                                          ReadinessAlertService alerts, GovernanceAudit audit, GovernanceCaller caller) {
        return new ReadinessGovernanceService(session, links, readiness, candidates, alerts, audit, caller, properties,
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    SchedulerLease jdbcSchedulerLease(JdbcTemplate jdbc) {
        return new JdbcSchedulerLease(jdbc);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    SchedulerLease mongoSchedulerLease(MongoTemplate mongo) {
        return new MongoSchedulerLease(mongo, Clock.systemUTC());
    }

    @Bean
    ReadinessRecheckService readinessRecheckService(TenantApplicationServiceUseCase links, ApplicationReadinessUseCase readiness,
                                                    ReadinessAlertService alerts, SchedulerLease lease, TenantRepository tenants) {
        Predicate<String> inService = tenantId -> tenants.findById(tenantId)
                .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE).orElse(false);
        return new ReadinessRecheckService(links, readiness, alerts, lease, inService, properties);
    }
}
