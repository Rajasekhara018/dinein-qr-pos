package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.DashboardPreference;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DashboardPreferenceAttributeConverter extends AbstractJsonAttributeConverter<DashboardPreference> {
    public DashboardPreferenceAttributeConverter() {
        super(DashboardPreference.class);
    }
}
