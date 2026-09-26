package com.heuristq.dinein.order;

import com.heuristq.dinein.shared.util.BusinessTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Allocates the per-day token (1, 2, 3 ... resetting at midnight IST) with an atomic upsert, and derives the
 * human-friendly order number {@code yyMMdd-NNN} from it.
 */
@Component
public class OrderNumberService {

    private static final DateTimeFormatter PREFIX = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OrderNumberService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public record Allocated(String orderNumber, int displayToken) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Allocated next() {
        LocalDate today = BusinessTime.today(clock);
        Integer value = jdbcTemplate.queryForObject("""
                INSERT INTO daily_order_counter (business_date, last_value) VALUES (?, 1)
                ON CONFLICT (business_date) DO UPDATE SET last_value = daily_order_counter.last_value + 1
                RETURNING last_value
                """, Integer.class, Date.valueOf(today));
        int token = value == null ? 1 : value;
        return new Allocated(today.format(PREFIX) + "-" + String.format("%03d", token), token);
    }
}
