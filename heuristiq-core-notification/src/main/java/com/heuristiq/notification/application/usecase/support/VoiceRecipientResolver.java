package com.railcab.nms.application.usecase.support;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.out.RecipientResolver;
import com.railcab.nms.domain.model.NotificationChannel;

/** VOICE calls go to the request's {@code toPhone}, like SMS. No phone, no call. */
@Component
public class VoiceRecipientResolver implements RecipientResolver {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.VOICE;
    }

    @Override
    public List<String> resolve(NotificationRequest request) {
        return StringUtils.hasText(request.getToPhone()) ? List.of(request.getToPhone()) : List.of();
    }
}
