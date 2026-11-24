package com.heuristiq.notification.application.dto;

import java.util.List;

public record EventConfigResponse(
        String label,
        List<String> attributes) {
}
