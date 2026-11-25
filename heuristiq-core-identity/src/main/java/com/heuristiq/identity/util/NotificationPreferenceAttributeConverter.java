package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.NotificationPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class NotificationPreferenceAttributeConverter extends AbstractJsonAttributeConverter<NotificationPreference> {
    public NotificationPreferenceAttributeConverter() {
        super(NotificationPreference.class);
    }
}
