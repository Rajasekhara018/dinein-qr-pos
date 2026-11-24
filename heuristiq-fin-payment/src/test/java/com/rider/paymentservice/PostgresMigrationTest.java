package com.heuristiq.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Flyway scripts of Rider_PaymentService on a real PostgreSQL (embedded binaries, no Docker): the whole chain must
 * apply from an empty database, end at the highest script version, and create this round's tenant_id objects and the amount
 * currency columns with their backfill and checks (V24).
 */
class PostgresMigrationTest {

    private static final String LATEST = "29";

    private static List<String> strings(DataSource ds, String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    private static void exec(DataSource ds, String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private static DataSource migrated(EmbeddedPostgres pg) {
        DataSource ds = pg.getPostgresDatabase();
        migrateTo(ds, null);
        return ds;
    }

    /** Runs the scripts up to {@code target} (all of them when null). */
    private static void migrateTo(DataSource ds, String target) {
        var config = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false"));
        if (target != null) {
            config.target(target);
        }
        config.load().migrate();
    }

    @Test
    void migratesFromEmptyAndCreatesTheTenantObjects() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = migrated(pg);

            assertThat(strings(ds, "select max(version::int)::text from flyway_schema_history where success"))
                    .containsExactly(LATEST);
            assertThat(strings(ds, "select version from flyway_schema_history where success order by installed_rank desc limit 1"))
                    .containsExactly(LATEST);
            for (String table : List.of("payments", "payment_refunds", "wallet_topup_sessions", "driver_payments", "driver_payouts", "driver_incentives", "driver_safety_deposits", "split_fare_requests", "wallet_transactions")) {
                assertThat(strings(ds, "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = 'tenant_id'", table))
                        .as("tenant_id on %s", table).containsExactly("tenant_id");
            }
            assertThat(strings(ds, "select indexname from pg_indexes where schemaname = 'public'"))
                    .contains("ix_payments_tenant", "ix_driver_payments_tenant", "ix_driver_payouts_tenant", "ix_driver_incentives_tenant", "ix_driver_safety_deposits_tenant", "ix_split_fare_requests_tenant", "ix_wallet_transactions_tenant", "ix_payment_refunds_tenant", "ix_wallet_topup_sessions_tenant", "idx_outbox_due",
                            "idx_payments_status_updated", "idx_payments_status_gateway", "idx_payments_created",
                            "idx_topup_status_gateway", "idx_wtx_legacy_refund");
        }
    }

    @Test
    void walletCreditMigrationAddsTheCreditColumns_andKeepsTheLegacyLedgerTables() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = migrated(pg);

            assertThat(strings(ds, "select column_name from information_schema.columns where table_schema = 'public' "
                    + "and table_name = 'wallet_topup_sessions' and column_name in "
                    + "('version','credit_attempts','credit_next_attempt_at','credit_last_error','credited_at','wallet_journal_id') "
                    + "order by column_name"))
                    .containsExactly("credit_attempts", "credit_last_error", "credit_next_attempt_at", "credited_at",
                            "version", "wallet_journal_id");
            // nothing is dropped: the old ledger stays, read-only, for reconciliation
            assertThat(strings(ds, "select table_name from information_schema.tables where table_schema = 'public' "
                    + "and table_name in ('wallet_accounts','wallet_transactions') order by table_name"))
                    .containsExactly("wallet_accounts", "wallet_transactions");

            exec(ds, "insert into wallet_topup_sessions (id, customer_id, amount, currency, gateway, status, idempotency_key, "
                    + "created_at, updated_at) values ('t1','c1',10,'INR','RAZORPAY','CAPTURED','k1',now(),now())");
            assertThat(strings(ds, "select credit_attempts || ',' || version from wallet_topup_sessions where id = 't1'"))
                    .containsExactly("0,0");
            exec(ds, "update wallet_topup_sessions set status = 'PAID_AWAITING_CREDIT' where id = 't1'");
            assertThat(strings(ds, "select status from wallet_topup_sessions where id = 't1'"))
                    .containsExactly("PAID_AWAITING_CREDIT");
        }
    }

    @Test
    void v24AddsACurrencyNextToEveryAmountBackfillsItAndKeepsTheTwoTogether() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            migrateTo(ds, "23");
            exec(ds, "insert into payments (internal_payment_id, ride_id, customer_id, amount, currency, method, idempotency_key, "
                    + "created_at, updated_at) values ('pay-aed', 'r1', 'c1', 100.00, 'aed', 'RAZORPAY', 'k-aed', now(), now())");
            exec(ds, "insert into payment_refunds (id, refund_reference, payment_id, amount, destination, status) "
                    + "values ('rf1', 'RF-1', 'pay-aed', 40.00, 'GATEWAY', 'COMPLETED'), "
                    + "('rf2', 'RF-2', 'pay-gone', 10.00, 'WALLET', 'COMPLETED')");
            exec(ds, "insert into wallet_transactions (id, user_id, amount) values ('w1', 'c1', 25.50), ('w2', 'c1', null)");
            exec(ds, "insert into driver_payments (id, driver_id, amount, platform_fee_percent, platform_fee, net_amount) "
                    + "values ('dp1', 'd1', 100.00, 20.000, 20.00, 80.00), ('dp2', 'd1', null, null, null, null)");
            exec(ds, "insert into driver_payouts (id, driver_id, requested_amount) values ('po1', 'd1', 250.50)");
            exec(ds, "insert into driver_safety_deposits (id, driver_id, amount) values ('sd1', 'd1', 500.00)");
            exec(ds, "insert into driver_incentives (id, driver_id, rule_name, bonus_amount, week_start_date, awarded_at) "
                    + "values ('in1', 'd1', 'TRIP_COUNT', 150.00, current_date, now())");
            exec(ds, "insert into split_fare_requests (id, ride_id, total_amount) values ('sf1', 'r1', 300.00)");
            exec(ds, "insert into split_fare_participants (split_fare_request_id, participant_order, share_amount) "
                    + "values ('sf1', 0, 150.00), ('sf1', 1, null)");

            migrateTo(ds, null);

            assertThat(strings(ds, "select table_name || '.' || column_name || ':' || data_type || ':' || character_maximum_length "
                    + "from information_schema.columns where table_schema = 'public' and column_name like '%currency' "
                    + "order by table_name, column_name"))
                    .containsExactly(
                            "driver_incentives.bonus_amount_currency:character varying:3",
                            "driver_payments.amount_currency:character varying:3",
                            "driver_payments.net_amount_currency:character varying:3",
                            "driver_payments.platform_fee_currency:character varying:3",
                            "driver_payouts.requested_amount_currency:character varying:3",
                            "driver_safety_deposits.amount_currency:character varying:3",
                            "payment_refund_accounting.amount_currency:character varying:3",
                            "payment_refunds.amount_currency:character varying:3",
                            "payments.currency:character varying:8",
                            "split_fare_participants.share_amount_currency:character varying:3",
                            "split_fare_requests.total_amount_currency:character varying:3",
                            "wallet_topup_sessions.currency:character varying:8",
                            "wallet_transactions.amount_currency:character varying:3");
            // No amount is left as a floating-point column.
            assertThat(strings(ds, "select table_name || '.' || column_name from information_schema.columns "
                    + "where table_schema = 'public' and data_type in ('double precision', 'real')")).isEmpty();

            // Refunds take their payment's currency (upper-cased); a refund whose payment is gone falls back to INR.
            assertThat(strings(ds, "select id || ' ' || amount::text || ' ' || amount_currency from payment_refunds order by id"))
                    .containsExactly("rf1 40.00 AED", "rf2 10.00 INR");
            assertThat(strings(ds, "select id || ' ' || coalesce(amount_currency, '-') from wallet_transactions order by id"))
                    .containsExactly("w1 INR", "w2 -");
            assertThat(strings(ds, "select id || ' ' || coalesce(amount_currency, '-') || ' ' || coalesce(platform_fee_currency, '-') "
                    + "|| ' ' || coalesce(net_amount_currency, '-') || ' ' || coalesce(platform_fee_percent::text, '-') "
                    + "from driver_payments order by id"))
                    .containsExactly("dp1 INR INR INR 20.000", "dp2 - - - -");
            assertThat(strings(ds, "select requested_amount::text || ' ' || requested_amount_currency from driver_payouts"))
                    .containsExactly("250.50 INR");
            assertThat(strings(ds, "select amount::text || ' ' || amount_currency from driver_safety_deposits"))
                    .containsExactly("500.00 INR");
            assertThat(strings(ds, "select bonus_amount::text || ' ' || bonus_amount_currency from driver_incentives"))
                    .containsExactly("150.00 INR");
            assertThat(strings(ds, "select total_amount::text || ' ' || total_amount_currency from split_fare_requests"))
                    .containsExactly("300.00 INR");
            assertThat(strings(ds, "select participant_order || ' ' || coalesce(share_amount_currency, '-') "
                    + "from split_fare_participants order by participant_order"))
                    .containsExactly("0 INR", "1 -");

            // The checks keep an amount and its currency together.
            assertThatThrownBy(() -> exec(ds, "insert into payment_refunds (id, refund_reference, payment_id, amount, destination, status) "
                    + "values ('rf3', 'RF-3', 'pay-aed', 5.00, 'GATEWAY', 'PROCESSING')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_payment_refunds_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into driver_payments (id, driver_id, amount, amount_currency, platform_fee, "
                    + "net_amount, net_amount_currency) values ('dp3', 'd1', 10.00, 'INR', 2.00, 8.00, 'INR')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_driver_payments_platform_fee_currency");
            assertThatThrownBy(() -> exec(ds, "insert into driver_payouts (id, driver_id, requested_amount_currency) "
                    + "values ('po2', 'd1', 'INR')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_driver_payouts_requested_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into driver_safety_deposits (id, driver_id, amount) values ('sd2', 'd1', 1.00)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_driver_safety_deposits_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into split_fare_requests (id, ride_id, total_amount) values ('sf2', 'r1', 1.00)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_split_fare_requests_total_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into split_fare_participants (split_fare_request_id, participant_order, "
                    + "share_amount) values ('sf1', 2, 1.00)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_split_fare_participants_share_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into wallet_transactions (id, user_id, amount) values ('w3', 'c1', 1.00)"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_wallet_transactions_amount_currency");
            assertThatThrownBy(() -> exec(ds, "insert into driver_incentives (id, driver_id, rule_name, bonus_amount, "
                    + "bonus_amount_currency, week_start_date, awarded_at) "
                    + "values ('in2', 'd2', 'TRIP_COUNT', 1.00, null, current_date, now())"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_driver_incentives_bonus_amount_currency");

            // An incentive relying on the amount's default (0) gets the default currency too; another currency is kept.
            exec(ds, "insert into driver_incentives (id, driver_id, rule_name, week_start_date, awarded_at) "
                    + "values ('in3', 'd3', 'TRIP_COUNT', current_date, now())");
            assertThat(strings(ds, "select bonus_amount::text || ' ' || bonus_amount_currency from driver_incentives where id = 'in3'"))
                    .containsExactly("0.00 INR");
            exec(ds, "insert into payment_refunds (id, refund_reference, payment_id, amount, amount_currency, destination, status) "
                    + "values ('rf4', 'RF-4', 'pay-aed', 5.00, 'AED', 'GATEWAY', 'PROCESSING')");
            assertThat(strings(ds, "select amount_currency from payment_refunds where id = 'rf4'")).containsExactly("AED");
        }
    }

    /** V27: a direct collection's UPI_DIRECT method is accepted next to CASH and RAZORPAY; anything else is kept out. */
    @Test
    void v27AcceptsUpiDirectAndRefusesAnUnknownMethod() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = migrated(pg);
            exec(ds, "insert into payments (internal_payment_id, ride_id, customer_id, amount, currency, method, status, idempotency_key, "
                    + "created_at, updated_at) values ('pay-upi', 'r1', 'c1', 100.00, 'INR', 'UPI_DIRECT', 'CAPTURED', "
                    + "'direct-collection:r1', now(), now())");
            assertThat(strings(ds, "select method from payments where internal_payment_id = 'pay-upi'")).containsExactly("UPI_DIRECT");
            assertThatThrownBy(() -> exec(ds, "insert into payments (internal_payment_id, ride_id, customer_id, amount, currency, "
                    + "method, idempotency_key, created_at, updated_at) values ('pay-x', 'r2', 'c1', 1.00, 'INR', 'CHEQUE', 'k-x', "
                    + "now(), now())")).isInstanceOf(SQLException.class).hasMessageContaining("ck_payments_method");
        }
    }
}
