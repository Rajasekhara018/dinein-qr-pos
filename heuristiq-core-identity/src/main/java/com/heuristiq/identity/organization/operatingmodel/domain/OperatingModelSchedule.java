package com.heuristiq.identity.organization.operatingmodel.domain;

import static com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelConstants.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An organization's operating-model schedule: the changes of model, oldest first. The model on a day is the latest change
 * starting on or before it; with no change it is {@link OperatingModelConstants#DEFAULT_MODEL}. Changes already in force are
 * never changed, so a past sale always keeps the model it was booked under. Immutable: the change methods return a new
 * schedule.
 */
public final class OperatingModelSchedule {

    private final List<OperatingModelPeriod> periods;

    private OperatingModelSchedule(List<OperatingModelPeriod> periods) {
        this.periods = List.copyOf(periods);
    }

    /** A schedule with no changes (the organization has never chosen a model). */
    public static OperatingModelSchedule empty() {
        return new OperatingModelSchedule(List.of());
    }

    /**
     * The schedule of stored changes, which must be in strictly increasing date order. A stored schedule out of order fails
     * loudly instead of being guessed at: treating a franchise organization as DIRECT would book sales with the wrong operator.
     */
    public static OperatingModelSchedule of(List<OperatingModelPeriod> stored) {
        List<OperatingModelPeriod> periods = stored == null ? List.of() : stored;
        for (int i = 1; i < periods.size(); i++) {
            if (!periods.get(i).effectiveFrom().isAfter(periods.get(i - 1).effectiveFrom())) {
                throw new IllegalStateException(ERROR_MALFORMED);
            }
        }
        return new OperatingModelSchedule(periods);
    }

    /** The changes, oldest first. */
    public List<OperatingModelPeriod> periods() {
        return periods;
    }

    /** The model in force on a day. */
    public OperatingModel modelOn(LocalDate day) {
        OperatingModel model = DEFAULT_MODEL;
        for (OperatingModelPeriod period : periods) {
            if (period.effectiveFrom().isAfter(day)) {
                break;
            }
            model = period.model();
        }
        return model;
    }

    /**
     * Schedules a change of model from a day. The day must be today or later and after every existing change, and the model
     * must differ from the one that would otherwise be in force then.
     */
    public OperatingModelSchedule withChange(OperatingModel model, LocalDate effectiveFrom, LocalDate today) {
        if (model == null) {
            throw OperatingModelException.invalid(ERROR_MODEL_REQUIRED);
        }
        if (effectiveFrom == null) {
            throw OperatingModelException.invalid(ERROR_DATE_REQUIRED);
        }
        if (effectiveFrom.isBefore(today)) {
            throw OperatingModelException.invalid(ERROR_DATE_IN_PAST);
        }
        if (!periods.isEmpty() && !effectiveFrom.isAfter(periods.getLast().effectiveFrom())) {
            throw OperatingModelException.conflict(ERROR_DATE_NOT_AFTER_LAST);
        }
        if (modelOn(effectiveFrom) == model) {
            throw OperatingModelException.conflict(ERROR_SAME_MODEL);
        }
        List<OperatingModelPeriod> next = new ArrayList<>(periods);
        next.add(new OperatingModelPeriod(model, effectiveFrom));
        return new OperatingModelSchedule(next);
    }

    /** Only the changes already in force on a day (starting on or before it); every later, not yet started change is dropped. */
    public OperatingModelSchedule inForceOn(LocalDate today) {
        return new OperatingModelSchedule(periods.stream().filter(p -> !p.effectiveFrom().isAfter(today)).toList());
    }

    /** The changes that start after a day (not yet in force then). */
    public List<OperatingModelPeriod> startingAfter(LocalDate day) {
        return periods.stream().filter(p -> p.effectiveFrom().isAfter(day)).toList();
    }

    /**
     * Replaces every change that has not started yet with the requested ones (oldest first); the changes in force stay as
     * they are. An empty list cancels every scheduled change. Each requested change is validated as by {@link #withChange}.
     */
    public OperatingModelSchedule withFutureChanges(List<OperatingModelPeriod> requested, LocalDate today) {
        OperatingModelSchedule next = inForceOn(today);
        for (OperatingModelPeriod change : requested == null ? List.<OperatingModelPeriod>of() : requested) {
            if (change == null) {
                throw OperatingModelException.invalid(ERROR_MODEL_REQUIRED);
            }
            next = next.withChange(change.model(), change.effectiveFrom(), today);
        }
        return next;
    }

    /** The changes as plain maps, the shape written to the audit trail. */
    public List<Map<String, String>> toList() {
        List<Map<String, String>> entries = new ArrayList<>();
        for (OperatingModelPeriod period : periods) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put(MODEL_FIELD, period.model().name());
            entry.put(EFFECTIVE_FROM_FIELD, period.effectiveFrom().toString());
            entries.add(entry);
        }
        return entries;
    }
}
