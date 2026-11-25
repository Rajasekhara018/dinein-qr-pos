package com.heuristiq.identity.util;

import com.heuristiq.identity.helper.MfaPolicy;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MfaPolicyAttributeConverter extends AbstractJsonAttributeConverter<MfaPolicy> {
    public MfaPolicyAttributeConverter() {
        super(MfaPolicy.class);
    }
}
