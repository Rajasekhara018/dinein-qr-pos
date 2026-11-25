package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.RegionalPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RegionalPreferenceAttributeConverter extends AbstractJsonAttributeConverter<RegionalPreference> {
    public RegionalPreferenceAttributeConverter() {
        super(RegionalPreference.class);
    }
}
