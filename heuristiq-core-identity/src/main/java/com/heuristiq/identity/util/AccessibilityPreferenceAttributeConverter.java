package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.AccessibilityPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AccessibilityPreferenceAttributeConverter extends AbstractJsonAttributeConverter<AccessibilityPreference> {
    public AccessibilityPreferenceAttributeConverter() {
        super(AccessibilityPreference.class);
    }
}
