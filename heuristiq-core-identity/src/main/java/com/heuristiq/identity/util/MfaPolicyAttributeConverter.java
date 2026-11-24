package com.heuristiq.uam.util;

import com.heuristiq.uam.helper.MfaPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MfaPolicyAttributeConverter extends AbstractJsonAttributeConverter<MfaPolicy> {
    public MfaPolicyAttributeConverter() {
        super(MfaPolicy.class);
    }
}
