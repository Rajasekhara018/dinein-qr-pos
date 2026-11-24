package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.DashboardPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DashboardPreferenceAttributeConverter extends AbstractJsonAttributeConverter<DashboardPreference> {
    public DashboardPreferenceAttributeConverter() {
        super(DashboardPreference.class);
    }
}
