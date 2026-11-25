package com.heuristiq.notification.application.usecase.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.out.RecipientResolver;
import com.heuristiq.notification.domain.model.NotificationChannel;

@Component
public class EmailRecipientResolver implements RecipientResolver {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public List<String> resolve(NotificationRequest request) {
        return request.getToEmail() != null ? request.getToEmail() : List.of();
    }
}
