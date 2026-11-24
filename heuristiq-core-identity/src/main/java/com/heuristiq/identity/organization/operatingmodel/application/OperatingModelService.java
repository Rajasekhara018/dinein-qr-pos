package com.heuristiq.uam.organization.operatingmodel.application;

import static com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants.ERROR_ORGANIZATION_NOT_FOUND;

import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelAudit;
import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.uam.organization.operatingmodel.application.port.OrganizationDirectory;
import com.heuristiq.uam.organization.operatingmodel.application.port.OrganizationDirectory.Organization;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelSchedule;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps an organization's operating-model schedule. "Today" is the organization's own day (its time zone, or the configured
 * default), so a change dated today starts at the organization's midnight, not the server's. Refusals are thrown as
 * {@code OperatingModelException}; an unknown organization as {@link OrganizationNotFoundException}.
 */
public class OperatingModelService implements OperatingModelUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperatingModelService.class);

    private final OrganizationDirectory organizations;
    private final OperatingModelScheduleStore schedules;
    private final OperatingModelAudit audit;
    private final Clock clock;
    private final ZoneId defaultZone;

    public OperatingModelService(OrganizationDirectory organizations, OperatingModelScheduleStore schedules, OperatingModelAudit audit,
                                 Clock clock, ZoneId defaultZone) {
        this.organizations = organizations;
        this.schedules = schedules;
        this.audit = audit;
        this.clock = clock;
        this.defaultZone = defaultZone;
    }

    @Override
    public OperatingModelView view(String organizationId) {
        Organization organization = load(organizationId);
        return toView(organization.id(), OperatingModelSchedule.of(schedules.find(organization.id())), today(organization));
    }

    @Override
    public OperatingModelView scheduleFutureChanges(String organizationId, List<OperatingModelPeriod> changes, String actorUserId) {
        Organization organization = load(organizationId);
        LocalDate today = today(organization);
        OperatingModelSchedule before = OperatingModelSchedule.of(schedules.find(organization.id()));
        OperatingModelSchedule after = before.withFutureChanges(changes, today);
        if (!after.periods().equals(before.periods())) {
            schedules.replace(organization.id(), before.periods(), after.periods(), actorUserId, clock.instant());
            audit.scheduleChanged(organization.id(), actorUserId, before.toList(), after.toList());
        }
        return toView(organization.id(), after, today);
    }

    @Override
    public ModelOnDay modelOn(String organizationId, LocalDate day) {
        Organization organization = load(organizationId);
        LocalDate date = day == null ? today(organization) : day;
        return new ModelOnDay(organization.id(), date, OperatingModelSchedule.of(schedules.find(organization.id())).modelOn(date),
                zoneOf(organization).getId());
    }

    private Organization load(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new OrganizationNotFoundException(ERROR_ORGANIZATION_NOT_FOUND);
        }
        return organizations.find(organizationId.trim()).orElseThrow(() -> new OrganizationNotFoundException(ERROR_ORGANIZATION_NOT_FOUND));
    }

    private LocalDate today(Organization organization) {
        return LocalDate.now(clock.withZone(zoneOf(organization)));
    }

    private ZoneId zoneOf(Organization organization) {
        String timeZone = organization.timeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return defaultZone;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            log.warn("operating-model.time-zone.invalid organization={} timeZone={} using={}", organization.id(), timeZone, defaultZone);
            return defaultZone;
        }
    }

    private static OperatingModelView toView(String organizationId, OperatingModelSchedule schedule, LocalDate today) {
        List<PeriodView> periods = schedule.periods().stream()
                .map(p -> new PeriodView(p.model(), p.effectiveFrom(), !p.effectiveFrom().isAfter(today)))
                .toList();
        return new OperatingModelView(organizationId, today, schedule.modelOn(today), periods);
    }
}
