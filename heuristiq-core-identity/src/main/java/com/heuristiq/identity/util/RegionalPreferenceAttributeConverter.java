package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.RegionalPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RegionalPreferenceAttributeConverter extends AbstractJsonAttributeConverter<RegionalPreference> {
    public RegionalPreferenceAttributeConverter() {
        super(RegionalPreference.class);
    }
}
