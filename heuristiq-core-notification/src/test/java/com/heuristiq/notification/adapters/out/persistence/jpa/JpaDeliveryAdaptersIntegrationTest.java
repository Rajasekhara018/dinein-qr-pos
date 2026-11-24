package com.heuristiq.notification.adapters.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.DeliveryReceipt;
import com.heuristiq.notification.application.dto.ReceiptOutcome;
import com.heuristiq.notification.application.usecase.DeliveryReceiptService;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.domain.model.AuditOutboxEntry;
import com.heuristiq.notification.domain.model.AuditOutboxStatus;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationLog;
import com.heuristiq.notification.domain.model.NotificationMessage;
import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.domain.repository.IAuditOutboxRepository;
import com.heuristiq.notification.domain.repository.INotificationAttemptRepository;
import com.heuristiq.notification.domain.repository.INotificationLogRepository;
import com.heuristiq.notification.domain.repository.INotificationMessageRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

/**
 * JPA persistence mode (MySQL/Postgres adapters) against an in-memory H2 in PostgreSQL mode, with the real
 * Spring Data repositories and transactions: per-attempt records, optimistic locking, the audit outbox
 * lease, and origin columns.
 */
@SpringJUnitConfig(JpaDeliveryAdaptersIntegrationTest.Config.class)
@TestPropertySource(properties = "nms.persistence.type=postgres")
class JpaDeliveryAdaptersIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 6, 10, 0, 0);

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = NotificationAttemptJpaSpringRepository.class)
    @Import({NotificationAttemptJpaRepositoryAdapter.class, AuditOutboxJpaRepositoryAdapter.class,
            NotificationLogJpaRepositoryAdapter.class, NotificationMessageJpaRepositoryAdapter.class,
            TemplateJpaRepositoryAdapter.class, EventDefinitionJpaRepositoryAdapter.class})
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:nms_jpa_delivery_it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.heuristiq.notification.adapters.out.persistence.jpa");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                    "hibernate.implicit_naming_strategy", "org.springframework.boot.hibernate.SpringImplicitNamingStrategy"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
    }

    @Autowired
    private INotificationAttemptRepository attempts;
    @Autowired
    private IAuditOutboxRepository outbox;
    @Autowired
    private INotificationLogRepository logs;
    @Autowired
    private INotificationMessageRepository messages;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DELETE FROM nms_delivery_attempts");
        jdbc.execute("DELETE FROM nms_audit_outbox");
        jdbc.execute("DELETE FROM nms_logs");
        jdbc.execute("DELETE FROM nms_messages");
    }

    private NotificationAttempt.NotificationAttemptBuilder attempt(String logId, int number) {
        return NotificationAttempt.builder().logId(logId).messageId("m-1").correlationId("c-1").event("OTP_SENT")
                .sourceService("rider-uam").eventCode("OTP_SENT").triggeringUserId("u-1").entityType("USER").entityId("u-1")
                .channel(NotificationChannel.SMS).providerName("digimile").providerMessageId("pm-" + logId + "-" + number)
                .attemptNumber(number).status(DeliveryStatus.SENT).recipient("******3210").latencyMs(42L)
                .startedAt(T0.plusSeconds(number)).completedAt(T0.plusSeconds(number));
    }

    // ── attempts ─────────────────────────────────────────────────────────────

    @Test
    void anAttemptRoundTripsWithEveryField() {
        NotificationAttempt saved = attempts.save(attempt("log-1", 1).status(DeliveryStatus.FAILED)
                .errorCode("DIGIMILE_1707").errorMessage("rejected").providerStatus("1707").build());

        NotificationAttempt read = attempts.findById(saved.getId()).orElseThrow();

        assertThat(read).usingRecursiveComparison().ignoringFields("version").isEqualTo(saved.toBuilder().version(null).build());
        assertThat(read.getVersion()).isNotNull();
        assertThat(read.getSourceService()).isEqualTo("rider-uam");
        assertThat(read.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void attemptsOfAMessageAndOfACorrelationComeBackOldestFirst() {
        attempts.save(attempt("log-1", 2).build());
        attempts.save(attempt("log-1", 1).build());
        attempts.save(attempt("log-2", 1).messageId("m-2").build());
        attempts.save(attempt("log-3", 1).messageId("m-3").correlationId("c-other").build());

        assertThat(attempts.findByMessageId("m-1", new PageQuery(0, 10)).content())
                .extracting(NotificationAttempt::getAttemptNumber).containsExactly(1, 2);
        assertThat(attempts.findByCorrelationId("c-1", new PageQuery(0, 10)).totalElements()).isEqualTo(3);
        assertThat(attempts.findByCorrelationId("c-1", new PageQuery(0, 2)).content()).hasSize(2);
        assertThat(attempts.findByMessageId("missing", new PageQuery(0, 10)).content()).isEmpty();
        assertThat(attempts.findByLogId("log-1")).extracting(NotificationAttempt::getAttemptNumber).containsExactly(1, 2);
    }

    @Test
    void aReceiptLookupFindsTheNewestAttemptWithThatProviderMessageId() {
        attempts.save(attempt("log-1", 1).providerMessageId("shared").build());
        NotificationAttempt newer = attempts.save(attempt("log-2", 1).providerMessageId("shared").startedAt(T0.plusMinutes(5)).build());

        assertThat(attempts.findLatestByProviderMessageId("shared").orElseThrow().getId()).isEqualTo(newer.getId());
        assertThat(attempts.findLatestByProviderMessageId("nope")).isEmpty();
    }

    @Test
    void theSameLogAndAttemptNumberCannotBeStoredTwice() {
        attempts.save(attempt("log-1", 1).build());

        assertThatThrownBy(() -> attempts.save(attempt("log-1", 1).providerMessageId("other").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aStaleUpdateIsRejectedByTheOptimisticLock() {
        NotificationAttempt saved = attempts.save(attempt("log-1", 1).build());
        NotificationAttempt first = attempts.findById(saved.getId()).orElseThrow();
        NotificationAttempt second = attempts.findById(saved.getId()).orElseThrow();

        attempts.save(first.toBuilder().status(DeliveryStatus.DELIVERED).build());

        assertThatThrownBy(() -> attempts.save(second.toBuilder().status(DeliveryStatus.FAILED).build()))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(attempts.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void retentionPurgeDeletesOnlyOldAttempts() {
        attempts.save(attempt("log-old", 1).startedAt(T0.minusDays(100)).build());
        attempts.save(attempt("log-new", 1).build());

        attempts.purgeOlderThan(T0.minusDays(90));

        assertThat(attempts.findByLogId("log-old")).isEmpty();
        assertThat(attempts.findByLogId("log-new")).hasSize(1);
    }

    // ── log / message origin columns ─────────────────────────────────────────

    @Test
    void theLogRowKeepsTheOriginAndTheNewStatuses() {
        NotificationLog saved = logs.save(NotificationLog.builder().messageId("m-1").correlationId("c-1").event("OTP_SENT")
                .sourceService("rider-uam").eventCode("OTP_SENT").triggeringUserId("u-1").entityType("USER").entityId("u-1")
                .channel(NotificationChannel.SMS).status(DeliveryStatus.DELIVERED).success(true).sentAt(T0).build());

        NotificationLog read = logs.findById(saved.getId()).orElseThrow();

        assertThat(read.getSourceService()).isEqualTo("rider-uam");
        assertThat(read.getEventCode()).isEqualTo("OTP_SENT");
        assertThat(read.getTriggeringUserId()).isEqualTo("u-1");
        assertThat(read.getEntityType()).isEqualTo("USER");
        assertThat(read.getEntityId()).isEqualTo("u-1");
        assertThat(read.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void aLogRowWithoutOriginStillSavesAndLoads() {
        NotificationLog saved = logs.save(NotificationLog.builder().event("RIDE_BOOKED").channel(NotificationChannel.EMAIL)
                .status(DeliveryStatus.SENT).success(true).sentAt(T0).build());

        NotificationLog read = logs.findById(saved.getId()).orElseThrow();

        assertThat(read.getSourceService()).isNull();
        assertThat(read.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void theMessageKeepsTheOrigin() {
        NotificationMessage m = new NotificationMessage();
        m.setMessageId("m-9");
        m.setEvent("OTP_SENT");
        m.setSourceService("rider-uam");
        m.setEventCode("OTP_SENT");
        m.setTriggeringUserId("u-1");
        m.setEntityType("USER");
        m.setEntityId("u-1");
        m.setStatus(NotificationMessageStatus.PROCESSING);
        m.setCreatedAt(T0);
        m.setUpdatedAt(T0);
        messages.insert(m);

        NotificationMessage read = messages.findByMessageId("m-9").orElseThrow();

        assertThat(read.getSourceService()).isEqualTo("rider-uam");
        assertThat(read.getEntityType()).isEqualTo("USER");
        assertThat(read.getEntityId()).isEqualTo("u-1");
    }

    // ── outbox ───────────────────────────────────────────────────────────────

    private AuditOutboxEntry entry(String eventId) {
        return AuditOutboxEntry.builder().eventId(eventId).payload("{\"eventId\":\"" + eventId + "\"}")
                .status(AuditOutboxStatus.PENDING).nextAttemptAt(T0).createdAt(T0).expireAt(T0.plusDays(7)).build();
    }

    @Test
    void enqueueingTheSameEventTwiceStoresItOnce() {
        assertThat(outbox.insertIfAbsent(entry("evt-00000001"))).isTrue();
        assertThat(outbox.insertIfAbsent(entry("evt-00000001"))).isFalse();

        assertThat(outbox.findDue(T0, 10)).hasSize(1);
    }

    @Test
    void onlyOneInstanceWinsTheLeaseAndAnExpiredLeaseCanBeTakenOver() {
        outbox.insertIfAbsent(entry("evt-00000001"));
        String id = outbox.findDue(T0, 10).get(0).getId();

        assertThat(outbox.tryLease(id, "instance-a", T0, T0.plusSeconds(60))).isTrue();
        assertThat(outbox.tryLease(id, "instance-b", T0.plusSeconds(1), T0.plusSeconds(61))).isFalse();
        assertThat(outbox.findDue(T0.plusSeconds(1), 10)).isEmpty();
        assertThat(outbox.tryLease(id, "instance-b", T0.plusSeconds(120), T0.plusSeconds(180))).isTrue();
    }

    @Test
    void sentFailedAndDeadEntriesAreTrackedAndOnlyDueOnesAreReturned() {
        outbox.insertIfAbsent(entry("evt-00000001"));
        outbox.insertIfAbsent(entry("evt-00000002"));
        outbox.insertIfAbsent(entry("evt-00000003"));
        List<AuditOutboxEntry> due = outbox.findDue(T0, 10);

        outbox.markSent(due.get(0).getId(), T0);
        outbox.markFailed(due.get(1).getId(), 1, T0.plusMinutes(5), "audit down", false);
        outbox.markFailed(due.get(2).getId(), 20, T0.plusMinutes(5), "audit down", true);

        assertThat(outbox.countByStatus(AuditOutboxStatus.SENT)).isEqualTo(1);
        assertThat(outbox.countByStatus(AuditOutboxStatus.PENDING)).isEqualTo(1);
        assertThat(outbox.countByStatus(AuditOutboxStatus.DEAD)).isEqualTo(1);
        assertThat(outbox.findDue(T0, 10)).isEmpty();
        assertThat(outbox.findDue(T0.plusMinutes(6), 10)).singleElement()
                .satisfies(e -> {
                    assertThat(e.getAttempts()).isEqualTo(1);
                    assertThat(e.getLastError()).isEqualTo("audit down");
                    assertThat(e.getLockedUntil()).isNull();
                });
    }

    @Test
    void expiredEntriesArePurged() {
        outbox.insertIfAbsent(entry("evt-00000001").toBuilder().expireAt(T0.minusDays(1)).build());
        outbox.insertIfAbsent(entry("evt-00000002"));

        outbox.purgeExpired(T0);

        assertThat(outbox.findDue(T0, 10)).extracting(AuditOutboxEntry::getEventId).containsExactly("evt-00000002");
    }

    // ── receipts on real stores ──────────────────────────────────────────────

    @Test
    void duplicateAndOutOfOrderReceiptsAreIdempotentOnRealTables() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);
        DeliveryReceiptService service = new DeliveryReceiptService(attempts, logs, new CommunicationAuditPublisher(outbox,
                new ObjectMapper().registerModule(new JavaTimeModule()), new NmsProperties(), clock), clock);
        NotificationLog log = logs.save(NotificationLog.builder().event("E").channel(NotificationChannel.SMS)
                .status(DeliveryStatus.SENT).success(true).sentAt(T0).build());
        NotificationAttempt saved = attempts.save(attempt(log.getId(), 1).providerMessageId("pm-x").build());

        DeliveryReceipt delivered = new DeliveryReceipt("digimile", "pm-x", DeliveryStatus.DELIVERED, "DELIVRD", null, null, null);
        assertThat(service.apply(delivered)).isEqualTo(ReceiptOutcome.APPLIED);
        assertThat(service.apply(delivered)).isEqualTo(ReceiptOutcome.DUPLICATE);
        assertThat(service.apply(new DeliveryReceipt("digimile", "pm-x", DeliveryStatus.SENT, "ENROUTE", null, null, null)))
                .isEqualTo(ReceiptOutcome.DUPLICATE);

        assertThat(attempts.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(logs.findById(log.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(outbox.findDue(T0.plusYears(1), 10)).hasSize(1);
    }
}
