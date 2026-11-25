package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.UiPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class UiPreferenceAttributeConverter extends AbstractJsonAttributeConverter<UiPreference> {
    public UiPreferenceAttributeConverter() {
        super(UiPreference.class);
    }
}
