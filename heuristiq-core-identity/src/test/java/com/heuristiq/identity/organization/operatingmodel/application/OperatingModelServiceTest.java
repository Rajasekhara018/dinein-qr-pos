package com.heuristiq.identity.organization.operatingmodel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.organization.operatingmodel.application.port.OperatingModelAudit;
import com.heuristiq.identity.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.identity.organization.operatingmodel.application.port.OrganizationDirectory;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelPeriod;
import com.heuristiq.identity.organization.operatingmodel.support.InMemoryOperatingModelScheduleStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The operating model is kept per organization, decided on the organization's own day, and audited when it changes. */
class OperatingModelServiceTest {

    // 2026-11-08 20:00 UTC is already 2026-11-09 in India and still 2026-11-08 in New York.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate INDIA_TODAY = LocalDate.of(2026, 11, 9);

    private final Map<String, String> timeZones = new HashMap<>();
    private InMemoryOperatingModelScheduleStore store;
    private FakeAudit audit;
    private OperatingModelService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryOperatingModelScheduleStore();
        audit = new FakeAudit();
        OrganizationDirectory directory = id -> timeZones.containsKey(id)
                ? Optional.of(new OrganizationDirectory.Organization(id, timeZones.get(id))) : Optional.empty();
        service = new OperatingModelService(directory, store, audit, CLOCK, ZoneId.of("Asia/Kolkata"));
        timeZones.put("acme", null);
        timeZones.put("globex", null);
    }

    private static List<OperatingModelPeriod> one(OperatingModel model, LocalDate from) {
        return List.of(new OperatingModelPeriod(model, from));
    }

    @Test
    void anOrganizationWithNoScheduleIsDirectToday() {
        OperatingModelUseCase.OperatingModelView view = service.view("acme");

        assertThat(view.currentModel()).isEqualTo(OperatingModel.DIRECT);
        assertThat(view.today()).isEqualTo(INDIA_TODAY);
        assertThat(view.schedule()).isEmpty();
    }

    @Test
    void todayIsTheOrganizationsOwnDayWhenItHasAValidTimeZone() {
        timeZones.put("ny", "America/New_York");
        timeZones.put("broken", "Not/AZone");

        assertThat(service.view("ny").today()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(service.view("broken").today()).isEqualTo(INDIA_TODAY);
    }

    @Test
    void schedulingSavesAndAuditsBeforeAndAfter() {
        OperatingModelUseCase.OperatingModelView view = service.scheduleFutureChanges("acme",
                one(OperatingModel.FRANCHISE, INDIA_TODAY), "admin-1");

        assertThat(view.currentModel()).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(view.schedule()).singleElement().satisfies(p -> {
            assertThat(p.model()).isEqualTo(OperatingModel.FRANCHISE);
            assertThat(p.inForce()).isTrue();
        });
        assertThat(store.find("acme")).isEqualTo(one(OperatingModel.FRANCHISE, INDIA_TODAY));
        assertThat(audit.calls).singleElement().satisfies(call -> {
            assertThat(call.organizationId()).isEqualTo("acme");
            assertThat(call.actor()).isEqualTo("admin-1");
            assertThat(call.before()).isEmpty();
            assertThat(call.after()).containsExactly(Map.of("model", "FRANCHISE", "effectiveFrom", "2026-11-09"));
        });
    }

    @Test
    void aFutureChangeIsShownAsNotYetInForce() {
        OperatingModelUseCase.OperatingModelView view = service.scheduleFutureChanges("acme",
                one(OperatingModel.FRANCHISE, INDIA_TODAY.plusDays(7)), "admin-1");

        assertThat(view.currentModel()).isEqualTo(OperatingModel.DIRECT);
        assertThat(view.schedule()).singleElement().extracting(OperatingModelUseCase.PeriodView::inForce).isEqualTo(false);
    }

    @Test
    void sendingTheSameScheduleAgainChangesNothingAndIsNotAudited() {
        List<OperatingModelPeriod> changes = one(OperatingModel.FRANCHISE, INDIA_TODAY.plusDays(7));
        service.scheduleFutureChanges("acme", changes, "admin-1");

        service.scheduleFutureChanges("acme", changes, "admin-1");

        assertThat(store.writes()).isEqualTo(1);
        assertThat(audit.calls).hasSize(1);
    }

    @Test
    void cancellingReadsTheStoredScheduleSoItAlwaysTakesEffect() {
        // Written by another instance or person: the service must see it, not an older copy.
        store.putDirectly("acme", one(OperatingModel.FRANCHISE, INDIA_TODAY.plusDays(1)));

        OperatingModelUseCase.OperatingModelView view = service.scheduleFutureChanges("acme", List.of(), "admin-1");

        assertThat(view.schedule()).isEmpty();
        assertThat(store.find("acme")).isEmpty();
        assertThat(audit.calls).hasSize(1);
    }

    @Test
    void aScheduleChangedBetweenReadAndWriteIsAConflictAndNotAudited() {
        OperatingModelScheduleStore racing = new OperatingModelScheduleStore() {
            @Override
            public List<OperatingModelPeriod> find(String organizationId) {
                return List.of();
            }

            @Override
            public void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next,
                                String userId, Instant at) {
                throw OperatingModelException.conflict(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
            }
        };
        OperatingModelService raced = new OperatingModelService(
                id -> Optional.of(new OrganizationDirectory.Organization(id, null)), racing, audit, CLOCK, ZoneId.of("Asia/Kolkata"));

        assertThatThrownBy(() -> raced.scheduleFutureChanges("acme", one(OperatingModel.FRANCHISE, INDIA_TODAY), "admin-1"))
                .isInstanceOf(OperatingModelException.class)
                .hasMessage(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
        assertThat(audit.calls).isEmpty();
    }

    @Test
    void aChangeInThePastIsRefusedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.scheduleFutureChanges("acme", one(OperatingModel.FRANCHISE, INDIA_TODAY.minusDays(1)), "admin-1"))
                .isInstanceOf(OperatingModelException.class)
                .hasMessage(OperatingModelConstants.ERROR_DATE_IN_PAST);
        assertThat(store.writes()).isZero();
        assertThat(audit.calls).isEmpty();
    }

    @Test
    void anUnknownOrganizationIsNotFound() {
        assertNotFound(() -> service.view("missing"));
        assertNotFound(() -> service.modelOn(" ", null));
        assertNotFound(() -> service.scheduleFutureChanges(null, List.of(), "admin-1"));
    }

    @Test
    void theInternalLookupAnswersForTodayOrAGivenDay() {
        service.scheduleFutureChanges("acme", one(OperatingModel.FRANCHISE, INDIA_TODAY.plusDays(7)), "admin-1");

        assertThat(service.modelOn("acme", null).model()).isEqualTo(OperatingModel.DIRECT);
        assertThat(service.modelOn("acme", null).date()).isEqualTo(INDIA_TODAY);
        assertThat(service.modelOn("acme", INDIA_TODAY.plusDays(7)).model()).isEqualTo(OperatingModel.FRANCHISE);
    }

    @Test
    void oneOrganizationsScheduleNeverAffectsAnother() {
        service.scheduleFutureChanges("acme", one(OperatingModel.FRANCHISE, INDIA_TODAY), "admin-1");

        assertThat(service.view("acme").currentModel()).isEqualTo(OperatingModel.FRANCHISE);
        assertThat(service.view("globex").currentModel()).isEqualTo(OperatingModel.DIRECT);
        assertThat(store.find("globex")).isEmpty();
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessage(OperatingModelConstants.ERROR_ORGANIZATION_NOT_FOUND);
    }

    private record AuditCall(String organizationId, String actor, List<Map<String, String>> before, List<Map<String, String>> after) {
    }

    private static final class FakeAudit implements OperatingModelAudit {
        private final List<AuditCall> calls = new ArrayList<>();

        @Override
        public void scheduleChanged(String organizationId, String actorUserId, List<Map<String, String>> before,
                                    List<Map<String, String>> after) {
            calls.add(new AuditCall(organizationId, actorUserId, before, after));
        }
    }
}
