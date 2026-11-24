package com.heuristiq.gateway.common.dto;

public record GatewayErrorResponse(String error, String message) {

    public static GatewayErrorResponse of(String error, String message) {
        return new GatewayErrorResponse(error, message);
    }
}
