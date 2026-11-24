package com.railcab.dms.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

// No @AllArgsConstructor: nothing in this codebase calls it, but its presence made Jackson 3
// select it as the deserialization creator -- and Jackson 3 treats a JSON-absent primitive
// constructor arg (e.g. doesInputHaveEncryptedData) as a hard error instead of defaulting it,
// breaking every write endpoint that takes this envelope (clients only ever set a subset of
// fields, typically just requestObject). @NoArgsConstructor + @Data's setters avoids that
// entirely: missing primitives just keep their Java default (false/0).
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonRequestObject {

    private String messageID;
    private String userData;
    private boolean doesInputHaveEncryptedData;
    private String updEncryptionIndex;
    private String requestType;
    private boolean success;
    private String message;
    private Object requestObject;
    private int pageNo;
    private int pageSize;
    private String sortField;
    private SortDirectionEnum sortOrder;
    private boolean useOrOperator;
    private String filePath;
    private String flagType;
    private String downloadType;
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
