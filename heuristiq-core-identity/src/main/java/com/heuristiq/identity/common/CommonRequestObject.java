package com.heuristiq.uam.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

// No @AllArgsConstructor: nothing in this codebase calls it (clients only ever set a subset of
// fields, typically just requestObject), but its presence made Jackson 3 select it as the
// deserialization creator -- and Jackson 3 treats a JSON-absent primitive constructor arg
// (e.g. doesInputHaveEncryptedData) as a hard error instead of defaulting it, breaking every
// write endpoint that takes this envelope. @NoArgsConstructor + @Data's setters avoids that
// entirely: missing primitives just keep their Java default (false/0).
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Generic request envelope used by most write/list endpoints in this service. "
        + "The actual typed payload travels in requestObject and is converted/validated server-side "
        + "via CommonRequestMapper against the endpoint's specific DTO.")
public class CommonRequestObject {
    @Schema(description = "Optional caller-supplied correlation id for this request.", example = "MSG-20260718-0001")
    private String messageID;
    @Schema(description = "Opaque caller/session context, not interpreted by this service.")
    private String userData;
    @Schema(description = "Whether requestObject's fields are encrypted and need decryption before use.")
    private boolean doesInputHaveEncryptedData;
    @Schema(description = "Index/key identifying which encryption configuration to use when doesInputHaveEncryptedData is true.")
    private String updEncryptionIndex;
    @Schema(description = "Caller-supplied classification of the request, not required by most endpoints.")
    private String requestType;
    private boolean success;
    private String message;
    @Schema(description = "The actual typed payload for this endpoint (e.g. UpsertUserRequest, LoginRequest), "
            + "converted server-side via CommonRequestMapper.requireRequestObject into the endpoint-specific DTO.")
    private Object requestObject;
    @Schema(description = "Zero-based page number for list endpoints.", example = "0")
    private int pageNo;
    @Schema(description = "Page size for list endpoints.", example = "50")
    private int pageSize;
    @Schema(description = "Field name to sort list results by.")
    private String sortField;
    @Schema(description = "Sort direction for list results.")
    private SortDirectionEnum sortOrder;
    @Schema(description = "Whether multiple filter criteria should be combined with OR instead of AND.")
    private boolean useOrOperator;
    @Schema(description = "Server-side file path, used only by file-oriented endpoints (e.g. export/import).")
    private String filePath;
    @Schema(description = "Caller-supplied flag/category used by a small number of specialized endpoints.")
    private String flagType;
    @Schema(description = "Requested download format, used only by export endpoints.")
    private String downloadType;
    @Schema(description = "Whether to render list results as a CSV download instead of JSON.")
    private Boolean downloadCSV;

    @JsonIgnore
    @Transient
    private CRUDEnum reqType;

    @JsonIgnore
    @Transient
    private CheckerEnum makerCheckerPath;

    @JsonIgnore
    @Transient
    private String reqUrl;

    @JsonIgnore
    @Transient
    private MessageEnum requestID;
}
