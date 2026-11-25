package com.heuristiq.identity.organization.operatingmodel.adapter.in;

import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase.ModelOnDay;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase.OperatingModelView;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Answer of the operating-model endpoint. */
@Schema(description = "An organization's operating model today and its schedule.")
public record OperatingModelResponse(
        @Schema(description = "Organization id") String organizationId,
        @Schema(description = "Today in the organization's time zone") LocalDate today,
        @Schema(description = "Model in force today") OperatingModel currentModel,
        @Schema(description = "Every change, oldest first") List<Change> schedule) {

    @Schema(description = "One change of model.")
    public record Change(OperatingModel model, LocalDate effectiveFrom,
                         @Schema(description = "True once it has started; such a change can no longer be changed") boolean inForce) {
    }

    /** Answer of the internal lookup used by booking services. */
    @Schema(description = "The model of an organization on one day.")
    public record OnDay(String organizationId, LocalDate date, OperatingModel model,
                        @Schema(description = "IANA time zone the organization's days are counted in") String timeZone) {

        static OnDay from(ModelOnDay model) {
            return new OnDay(model.organizationId(), model.date(), model.model(), model.timeZone());
        }
    }

    static OperatingModelResponse from(OperatingModelView view) {
        return new OperatingModelResponse(view.organizationId(), view.today(), view.currentModel(),
                view.schedule().stream().map(p -> new Change(p.model(), p.effectiveFrom(), p.inForce())).toList());
    }
}
