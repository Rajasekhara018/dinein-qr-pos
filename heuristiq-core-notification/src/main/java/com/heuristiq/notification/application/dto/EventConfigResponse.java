package com.railcab.nms.application.dto;

import java.util.List;

public record EventConfigResponse(
        String label,
        List<String> attributes) {
}
