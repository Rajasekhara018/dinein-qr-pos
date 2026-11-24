package com.railcab.nms.application.usecase.support;

import java.util.List;

import org.springframework.stereotype.Component;

import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.out.RecipientResolver;
import com.railcab.nms.application.port.out.UserPhoneDirectory;
import com.railcab.nms.domain.model.NotificationChannel;

/**
 * SMS goes to the request's {@code toPhone}; without one, to the numbers UAM holds for its {@code userIds}, so a service that knows
 * only who should be told (e.g. DMS's document expiry reminders) can still send an SMS. No number, no SMS.
 */
@Component
public class SmsRecipientResolver implements RecipientResolver {

    private final UserPhoneDirectory phones;

    /** Without a phone directory: SMS only to an explicit {@code toPhone} (tests). */
    public SmsRecipientResolver() {
        this(userIds -> java.util.Map.of());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SmsRecipientResolver(UserPhoneDirectory phones) {
        this.phones = phones;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public List<String> resolve(NotificationRequest request) {
        if (request.getToPhone() != null && !request.getToPhone().isBlank()) {
            return List.of(request.getToPhone());
        }
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            return List.copyOf(phones.phonesOf(request.getUserIds()).values());
        }
        return List.of();
    }
}
