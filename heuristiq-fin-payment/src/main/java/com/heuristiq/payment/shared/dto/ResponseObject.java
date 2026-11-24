package com.heuristiq.payment.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard response envelope shared across all endpoints — mirrors the monolith's ResponseObject. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseObject {
    private boolean status;
    private Object object;
    private String errorMsg;
}
