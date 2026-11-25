package com.heuristiq.identity.centralaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** The outbox insert must run in its own transaction so it can never abort or be rolled back with the business one. */
class JdbcCentralAuditOutboxTransactionTest {
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private DataSourceTransactionManager txManager;
    private JdbcCentralAuditOutbox outbox;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource("jdbc:h2:mem:outbox_tx_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V28__central_audit_outbox.sql")).execute(ds);
        jdbc = new NamedParameterJdbcTemplate(ds);
        jdbc.getJdbcTemplate().execute("create table biz (id int primary key)");
        txManager = new DataSourceTransactionManager(ds);
        outbox = new JdbcCentralAuditOutbox(jdbc, txManager);
    }

    @Test
    void theOutboxRowIsCommittedIndependentlyOfTheBusinessTransaction() {
        TransactionTemplate business = new TransactionTemplate(txManager);

        business.executeWithoutResult(status -> {
            jdbc.getJdbcTemplate().update("insert into biz values (1)");
            outbox.add("event-0001", "org-1", "{}", NOW);
            status.setRollbackOnly();
        });

        assertThat(jdbc.getJdbcTemplate().queryForObject("select count(*) from biz", Long.class)).isZero();
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void aFailingInsertDoesNotStopTheBusinessTransactionFromCommitting() {
        jdbc.getJdbcTemplate().execute("drop table central_audit_outbox");
        CentralAuditProperties props = new CentralAuditProperties(true, "http://audit", "tok", 100, 24, 10, 2000, 3000, 2, 5, 60000, 2000, 60000);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        CentralAuditPublisher publisher = new CentralAuditPublisher(props, outbox, events -> { }, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC), metrics);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            jdbc.getJdbcTemplate().update("insert into biz values (1)");
            publisher.publish(new CentralAuditEvent("event-0001", "DOMAIN", NOW, "rider-uam", null, "uam", "IDENTITY", "SUCCESS",
                    null, null, null, null, "org-1", null, null, "LOGIN", null));
        });

        assertThat(jdbc.getJdbcTemplate().queryForObject("select count(*) from biz", Long.class)).isEqualTo(1);
        assertThat(metrics.counter(CentralAuditConstants.METRIC_INSERT_FAILED).count()).isEqualTo(1);
    }

    @Test
    void overLongIdsAreCutToTheColumnLengthInsteadOfFailing() {
        String longId = "e".repeat(100);
        String longTenant = "t".repeat(100);

        outbox.add(longId, longTenant, "{}", NOW);

        assertThat(jdbc.getJdbcTemplate().queryForObject("select event_id from central_audit_outbox", String.class)).hasSize(64);
        assertThat(jdbc.getJdbcTemplate().queryForObject("select tenant_id from central_audit_outbox", String.class)).hasSize(64);
    }
}
