package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.UiPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class UiPreferenceAttributeConverter extends AbstractJsonAttributeConverter<UiPreference> {
    public UiPreferenceAttributeConverter() {
        super(UiPreference.class);
    }
}
