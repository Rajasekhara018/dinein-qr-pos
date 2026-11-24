package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.AccessibilityPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AccessibilityPreferenceAttributeConverter extends AbstractJsonAttributeConverter<AccessibilityPreference> {
    public AccessibilityPreferenceAttributeConverter() {
        super(AccessibilityPreference.class);
    }
}
