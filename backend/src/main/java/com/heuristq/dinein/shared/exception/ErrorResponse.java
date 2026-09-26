package com.heuristq.dinein.shared.exception;

import java.util.List;

public record ErrorResponse(String code, String message, List<?> details, String traceId) {
}
