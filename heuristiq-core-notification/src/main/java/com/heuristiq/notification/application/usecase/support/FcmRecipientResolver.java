package com.heuristiq.notification.application.usecase.support;

import com.heuristiq.notification.domain.repository.TenantScope;
import org.springframework.util.StringUtils;
import java.util.List;

import org.springframework.stereotype.Component;

import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.out.RecipientResolver;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.repository.IDeviceTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Explicit tokens take precedence; falls back to a device-token lookup by userIds so push can be
 * triggered by userId alone, the same way IN_APP/EMAIL/SMS already are.
 */
@Component
@RequiredArgsConstructor
public class FcmRecipientResolver implements RecipientResolver {

    private final IDeviceTokenRepository deviceTokenRepository;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.FCM_PUSH;
    }

    @Override
    public List<String> resolve(NotificationRequest request) {
        if (request.getFcmTokens() != null && !request.getFcmTokens().isEmpty()) {
            return request.getFcmTokens();
        }
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            // Push for an organization's notification only reaches tokens registered in that organization (or before
            // organizations existed); a notification with no organization is not limited.
            return StringUtils.hasText(request.getTenantId())
                    ? deviceTokenRepository.findTokensByUserIds(request.getUserIds(), TenantScope.limitedTo(request.getTenantId()))
                    : deviceTokenRepository.findTokensByUserIds(request.getUserIds());
        }
        return List.of();
    }
}
