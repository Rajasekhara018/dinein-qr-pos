package com.heuristiq.uam.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseObject {
    private boolean status;
    private Object object;
    private String errorMsg;

    @JsonProperty("success")
    public Boolean getSuccess() {
        return status;
    }

    @JsonProperty("success")
    public void setSuccess(Boolean success) {
        this.status = Boolean.TRUE.equals(success);
    }

    @JsonProperty("message")
    public String getMessage() {
        return errorMsg;
    }

    @JsonProperty("message")
    public void setMessage(String message) {
        this.errorMsg = message;
    }

    @JsonProperty("responseObject")
    public Object getResponseObject() {
        return object;
    }

    @JsonProperty("responseObject")
    public void setResponseObject(Object responseObject) {
        this.object = responseObject;
    }
}
