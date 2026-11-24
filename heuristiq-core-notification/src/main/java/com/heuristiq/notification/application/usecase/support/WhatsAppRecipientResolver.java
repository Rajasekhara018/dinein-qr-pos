package com.railcab.nms.application.usecase.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.out.RecipientResolver;
import com.railcab.nms.domain.model.NotificationChannel;

@Component
public class WhatsAppRecipientResolver implements RecipientResolver {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public List<String> resolve(NotificationRequest request) {
        return request.getToPhone() != null ? List.of(request.getToPhone()) : List.of();
    }
}
