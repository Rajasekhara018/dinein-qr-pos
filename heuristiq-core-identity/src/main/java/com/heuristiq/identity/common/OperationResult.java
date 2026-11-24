package com.heuristiq.uam.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Confirmation payload for a completed write operation that has no natural response body (delete/revoke/logout/etc).")
public record OperationResult(
        @Schema(description = "The operation performed.", example = "DELETE") String operation,
        @Schema(description = "The type of resource acted on.", example = "USER") String resource,
        @Schema(description = "The id of the affected resource, if applicable.") String id,
        @Schema(description = "Whether the operation completed successfully.", example = "true") boolean completed) {

    public static OperationResult completed(String operation, String resource, String id) {
        return new OperationResult(operation, resource, id, true);
    }
}
