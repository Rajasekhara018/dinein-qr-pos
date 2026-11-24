package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.NotificationPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class NotificationPreferenceAttributeConverter extends AbstractJsonAttributeConverter<NotificationPreference> {
    public NotificationPreferenceAttributeConverter() {
        super(NotificationPreference.class);
    }
}
