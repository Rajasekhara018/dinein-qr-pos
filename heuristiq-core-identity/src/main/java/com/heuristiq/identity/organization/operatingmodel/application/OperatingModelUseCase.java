package com.heuristiq.uam.organization.operatingmodel.application;

import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import java.time.LocalDate;
import java.util.List;

/** Reads and schedules an organization's operating model (DIRECT, FRANCHISE or HYBRID). */
public interface OperatingModelUseCase {

    /** One entry of the schedule; {@code inForce} is true once it has started. */
    record PeriodView(OperatingModel model, LocalDate effectiveFrom, boolean inForce) {
    }

    /** The organization's model today (in its own time zone) and its whole schedule, oldest first. */
    record OperatingModelView(String organizationId, LocalDate today, OperatingModel currentModel, List<PeriodView> schedule) {
    }

    /**
     * The model of an organization on one day.
     *
     * @param timeZone the IANA zone the organization's days are counted in (its own, or the platform default when it has none or
     *                 an invalid one); booking services compute a sale's day in it (docs/TIMEZONE_CONTRACT.md)
     */
    record ModelOnDay(String organizationId, LocalDate date, OperatingModel model, String timeZone) {

        /** A model with no time zone named. */
        public ModelOnDay(String organizationId, LocalDate date, OperatingModel model) {
            this(organizationId, date, model, null);
        }
    }

    OperatingModelView view(String organizationId);

    /**
     * Replaces every change that has not started yet with the given ones (oldest first); an empty list cancels them all.
     * Changes already in force are kept whatever is sent.
     */
    OperatingModelView scheduleFutureChanges(String organizationId, List<OperatingModelPeriod> changes, String actorUserId);

    /** The model on a day; a null day means today in the organization's time zone. */
    ModelOnDay modelOn(String organizationId, LocalDate day);
}
