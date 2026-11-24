package com.heuristq.dinein.notification;

import com.heuristq.dinein.notification.NotificationRequest.Recipients;
import com.heuristq.dinein.report.ReportService;
import com.heuristq.dinein.report.ReportService.DailyTotals;
import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.restaurant.domain.RestaurantRepository;
import com.heuristq.dinein.restaurant.domain.RestaurantStatus;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emails/notifies every active restaurant's owners a one-line summary of yesterday's sales. Runs once daily; a
 * restaurant with zero orders yesterday is skipped (nothing worth reporting).
 */
@Slf4j
@Component
public class DailySalesSummaryJob {

    private static final Set<StaffRole> OWNER = EnumSet.of(StaffRole.OWNER);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final RestaurantRepository restaurantRepository;
    private final StaffUserRepository staffUserRepository;
    private final ReportService reportService;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public DailySalesSummaryJob(RestaurantRepository restaurantRepository, StaffUserRepository staffUserRepository,
                               ReportService reportService, NotificationDispatcher dispatcher, Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.staffUserRepository = staffUserRepository;
        this.reportService = reportService;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.daily-summary-cron}", zone = "Asia/Kolkata")
    public void run() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        List<RestaurantEntity> restaurants = restaurantRepository.findAll();
        int sent = 0;
        for (RestaurantEntity restaurant : restaurants) {
            if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
                continue;
            }
            try {
                if (summarize(restaurant, yesterday)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                log.error("notification.daily_summary_failed restaurantId={}", restaurant.getId(), e);
            }
        }
        log.info("notification.daily_summary_sent count={} of={}", sent, restaurants.size());
    }

    private boolean summarize(RestaurantEntity restaurant, LocalDate date) {
        DailyTotals totals = reportService.dailyTotals(date, restaurant.getId());
        if (totals.ordersCount() == 0) {
            return false;
        }
        List<String> ownerEmails = staffUserRepository.findAllByRestaurantIdOrderByUsernameAsc(restaurant.getId())
                .stream().filter(u -> u.isActive() && u.getRole() == StaffRole.OWNER)
                .map(StaffUserEntity::getEmail).filter(e -> e != null && !e.isBlank()).distinct().toList();
        Map<String, String> data = new HashMap<>();
        data.put("date", date.format(DATE_FORMAT));
        data.put("ordersCount", String.valueOf(totals.ordersCount()));
        data.put("gross", totals.gross().toPlainString());
        dispatcher.dispatch(new NotificationRequest(NotificationEvent.DAILY_SUMMARY, null, restaurant.getId(), data,
                new Recipients(OWNER, ownerEmails, null, null)));
        return true;
    }
}
