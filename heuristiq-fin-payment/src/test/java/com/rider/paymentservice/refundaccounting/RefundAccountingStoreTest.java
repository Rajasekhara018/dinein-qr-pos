package com.heuristiq.paymentservice.refundaccounting;

import com.heuristiq.paymentservice.refundaccounting.adapter.out.JdbcRefundAccountingStore;
import com.heuristiq.paymentservice.refundaccounting.domain.RefundAccounting;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.math.BigDecimal;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class RefundAccountingStoreTest {
    @Test
    void deliverySurvivesRestartAndRetryAndCannotBeClaimedTwiceDuringItsLease() throws Exception {
        try (var pg = EmbeddedPostgres.builder().start()) {
            var ds = pg.getPostgresDatabase();
            Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                    .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).load().migrate();
            var jdbc = new NamedParameterJdbcTemplate(ds);
            var store = new JdbcRefundAccountingStore(jdbc);
            var now = Instant.now().plusSeconds(1);
            var original = new RefundAccounting.Posting("refund:1", "org-a", "ride-completed:1", BigDecimal.TEN, "INR", true, 0);
            var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
            tx.executeWithoutResult(status -> { store.enqueue(original); status.setRollbackOnly(); });
            assertThat(store.claim(now)).as("a rolled-back refund completion cannot leak accounting work").isEmpty();
            store.enqueue(original);
            store.enqueue(original);
            var claimed = store.claim(now);
            assertThat(claimed).hasSize(1);
            assertThat(claimed.getFirst().tenantId()).isEqualTo("org-a");
            assertThat(claimed.getFirst().creditAlreadyPosted()).isTrue();
            assertThat(store.claim(now)).isEmpty();
            store.retry(claimed.getFirst(), now.plusSeconds(30), "network");
            var restarted = new JdbcRefundAccountingStore(jdbc);
            assertThat(restarted.claim(now.plusSeconds(29))).isEmpty();
            var retry = restarted.claim(now.plusSeconds(31));
            assertThat(retry).hasSize(1);
            assertThat(retry.getFirst().attempts()).isEqualTo(1);
            restarted.delivered(retry.getFirst());
            assertThat(restarted.claim(now.plusSeconds(500))).isEmpty();
        }
    }
}
