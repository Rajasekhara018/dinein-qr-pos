package com.heuristiq.identity.organization.operatingmodel.domain;

import java.time.LocalDate;
import java.util.Objects;

/** One entry of an organization's operating-model schedule: the model in force from a day (in the organization's time zone). */
public record OperatingModelPeriod(OperatingModel model, LocalDate effectiveFrom) {

    public OperatingModelPeriod {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
